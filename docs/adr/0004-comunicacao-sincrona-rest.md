# ADR 0004 — Comunicação síncrona (REST) para o fluxo de liquidação, não orientada a eventos

## Status
Aceito para esta versão — candidato explícito a revisão (ver "Gatilhos" abaixo)

## Contexto
O desafio pede, como item separado do nível Staff/TL, uma "proposta de
arquitetura orientada a eventos (EDA) para o fluxo de liquidação" — um
documento à parte, ainda não escrito nesta entrega. Este ADR registra a
decisão mais imediata: por que o fluxo ATUAL é síncrono, e o que faria
sentido mudar isso.

## Decisão
O fluxo de aquisição → liquidação é uma chamada HTTP síncrona,
request/response, com tudo dentro de uma única transação ACID
(`SettlementService#settle`). Não há fila, broker de eventos, ou
processamento assíncrono no caminho principal.

## Alternativas consideradas
- **Liquidação assíncrona via evento** (ex.: `POST /settlements` publica um
  comando, um consumidor processa e emite um evento de resultado): reduz
  acoplamento temporal e permitiria escalar o processamento
  independentemente da API, mas troca uma resposta imediata e consistente
  ("liquidado, aqui está o valor") por uma resposta eventual — o operador
  da mesa (usuário real deste sistema) precisa do resultado imediato para
  decidir o próximo passo na tela. Para o volume atual (uma mesa de
  operações, não um pipeline de processamento em lote), a complexidade de
  coordenar consistência eventual não se paga ainda.
- **Publicar um evento de liquidação DEPOIS da transação síncrona
  (padrão outbox)**, para consumidores externos (ex.: sistema contábil)
  reagirem sem acoplar o `SettlementService` a eles diretamente: este é o
  caminho mais provável de evolução (ver Gatilhos), mas não foi
  implementado agora porque não há, hoje, nenhum consumidor real desse
  evento — construir a infraestrutura de outbox sem um consumidor é
  especular sobre uma necessidade que ainda não existe.

## Consequências
- Positivo: uma única transação ACID garante a atomicidade exigida pelo
  item 4.1.3 do desafio sem precisar de saga nem compensação — a resposta
  ao cliente já reflete o estado final, nunca um estado "processando".
- Negativo: o `SettlementService` é, hoje, o único ponto de verdade sobre
  uma liquidação — qualquer sistema externo que precise saber de uma
  liquidação (contábil, notificação ao cedente) teria que consultar a API
  ou o banco diretamente; não há um canal de eventos para consumir.

## Gatilhos para revisitar esta decisão
- Um segundo sistema (contábil, notificação, auditoria externa) precisar
  reagir a cada liquidação de forma desacoplada — o padrão outbox citado
  acima é o próximo passo natural, não uma reescrita.
- O volume de liquidações crescer a ponto de a latência da chamada
  síncrona ao banco (dentro da transação) se tornar o gargalo — nesse
  ponto, vale revisitar junto com o documento de design para 1M
  transações/minuto (item Staff/TL ainda pendente nesta entrega).
