# ADR 0002 — Monólito modular, não microsserviços

## Status
Aceito

## Contexto
O desafio lista explicitamente "microserviços para um case" como
anti-padrão (item 12) — mas vale registrar o raciocínio, não só obedecer.

## Decisão
Uma única aplicação Spring Boot, organizada em camadas internas
(`domain`, `pricing`, `repository`, `service`, `web`, `fx`) — não em
serviços implantáveis separadamente.

## Alternativas consideradas
- **Serviço de precificação separado do serviço de liquidação:** poderia
  parecer natural (Strategy de precificação é um domínio conceitualmente
  isolado), mas a liquidação PRECISA da precificação de forma síncrona e
  transacional (o preço calculado entra na mesma transação ACID que grava
  o registro de liquidação — ver `SettlementService`). Separar em
  serviços diferentes trocaria uma chamada de método por uma chamada de
  rede dentro do caminho crítico de uma transação financeira, sem nenhum
  ganho de isolamento real (as duas partes sempre escalam e implantam
  juntas neste domínio).
- **Serviço de câmbio separado:** este SIM é um candidato genuíno a
  extração futura — é a única integração externa do sistema, já isolada
  atrás de `ExternalFxRateProvider`/`ResilientFxRateGateway`. Não foi
  extraído agora porque não há necessidade de escalar ou implantar essa
  parte independentemente do resto ainda; a interface já modelada é o que
  tornaria essa extração barata no futuro, se o volume ou a equipe
  justificarem.

## Consequências
- Positivo: uma transação ACID cobrindo múltiplas tabelas sem
  coordenação distribuída (sagas, 2PC); deploy único, sem versionamento de
  contrato entre serviços para um domínio que muda junto.
- Negativo: qualquer escala futura de uma parte isolada (ex.: o volume de
  chamadas ao provedor de câmbio crescer independente do volume de
  liquidações) exige extrair um serviço depois, não antes — aceito
  conscientemente: a extração é mais barata de fazer depois, com dados
  reais de uso, do que adivinhar a fronteira certa agora.
