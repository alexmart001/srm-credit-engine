# Post-mortem — Liquidações Duplicadas (Anexo B)

Exercício de incidente do nível Staff/Tech Lead (seção 6 / Anexo B). Este
documento assume o papel de quem conduz a investigação na sexta-feira à
noite — não repete a análise de código do `REVIEW.md`, foca no processo de
resposta ao incidente: como confirmar a causa com evidência, o que conter
agora vs. corrigir depois, e o que muda para essa classe de erro não
voltar.

## 1. Linha do tempo hipotética

| Horário | Evento |
|---|---|
| 18:00 | Job de fechamento diário dispara liquidações automáticas para os recebíveis vencendo no dia (padrão operacional comum em FIDC — não é a mesa de operações clicando manualmente). |
| ~18:12–18:18 | Instabilidade de rede breve entre o orquestrador do batch e a API (causa ainda não confirmada — ver seção 2) gera timeouts em um subconjunto das chamadas a `POST /settlements`. |
| ~18:12–18:18 | O orquestrador do batch, ao não receber resposta dentro do timeout configurado, aplica sua política padrão de retry automático e reenvia a **mesma** requisição de liquidação para os mesmos recebíveis. |
| ~18:12–18:18 | O endpoint (Anexo A) processa a segunda requisição normalmente — não há verificação de idempotência nem de status do recebível — e gera uma **segunda** liquidação idêntica para cada recebível atingido pela instabilidade. |
| 18:40 | A mesa de operações é acionada por três cedentes reportando recebimento em duplicidade. Neste ponto, **não sabemos ainda se são exatamente três casos ou se há mais que ainda não foram notados** — isso é o primeiro fato a confirmar, não assumir. |

## 2. Causa raiz provável, e como eu confirmaria antes de agir

Duas hipóteses concorrentes, não uma certeza assumida de antemão:

**Hipótese A (mais provável dado "três cedentes", não "todos"): retry de
rede sem idempotência no servidor.** Um subconjunto de chamadas sofreu
timeout transitório, o cliente reenviou, e o servidor — sem chave de
idempotência — processou como uma operação nova.

**Hipótese B: escrita parcial silenciosa** (o `catch` vazio do Anexo A
engole a falha do segundo `UPDATE`, deixando o recebível como "não
liquidado" mesmo com uma liquidação já gravada — permitindo que uma
chamada totalmente independente, não um retry, liquide o mesmo recebível
de novo mais tarde).

**Como eu confirmaria, com evidência, antes de escrever qualquer correção:**

1. `SELECT receivable_id, COUNT(*) FROM settlements GROUP BY receivable_id HAVING COUNT(*) > 1;` — primeiro passo, sempre: **quantificar o blast radius real**, não confiar que são só os três cedentes que ligaram. É bem provável que existam mais casos ainda não percebidos pelo cedente.
2. Para os `receivable_id` duplicados: comparar os timestamps das duas linhas. Segundos/poucos minutos de diferença → consistente com retry de rede (Hipótese A). Horas de diferença, ou entre turnos de plantão diferentes → aponta mais para a Hipótese B ou para um terceiro fator (ex.: reprocessamento manual por engano).
3. Logs de acesso/gateway no intervalo 18:00–18:40: procurar por corpos de requisição idênticos (mesmo `receivableId`, mesmo `currency`) chegando duas vezes ao mesmo endpoint — e cruzar com os códigos de status retornados na primeira tentativa (timeout do lado do cliente não implica necessariamente erro do lado do servidor; o servidor pode ter respondido `200 OK` normalmente e a resposta ter se perdido no caminho).
4. Dashboard de infraestrutura (latência de rede, deploys, incidentes de outros serviços na mesma janela) — se houver um deploy ou pico de latência de rede correlacionado às 18:12–18:18, isso reforça a Hipótese A com uma causa física identificável, não só inferida pelos logs da aplicação.

Só depois desse levantamento eu declararia qual hipótese (ou combinação
das duas) é a causa raiz confirmada — declarar antes disso é adivinhação,
não investigação.

## 3. Ações imediatas (contenção) vs. correção definitiva

**Contenção (minutos, sem redeploy apressado):**
- Pausar o job de liquidação automática (ou o orquestrador que está
  retriando) — estanca o sangramento antes de entender o escopo total.
- Rodar a query da seção 2.1 para levantar **todos** os recebíveis
  afetados, não só os três já reportados.
- Para cada duplicidade confirmada: reverter o valor pago a mais junto à
  tesouraria, como um **novo registro de estorno** — nunca editando ou
  apagando o histórico já gravado (isso violaria a auditabilidade que é
  requisito do próprio sistema, item 4.1.4 do desafio).
- Comunicar proativamente os cedentes afetados ainda não identificados,
  antes que percebam sozinhos — gestão de risco reputacional, não só
  técnico.
- **Não** escrever e mergear um hotfix não testado sexta à noite sob
  pressão. Esse impulso é exatamente o que produziu o código do Anexo A em
  primeiro lugar ("gerado por IA e mergeado às pressas numa sexta-feira") —
  repeti-lo seria o pior aprendizado possível deste incidente. Se uma
  mitigação de código for genuinamente inadiável antes de segunda, a opção
  mais segura é desligar o fluxo automatizado (feature flag/kill
  switch) e liquidar manualmente os casos urgentes, não publicar uma
  correção não testada no mesmo caminho que já falhou.

**Correção definitiva (sem pressão de horário, com testes):**
Exatamente o que já está implementado neste repositório — não por
coincidência, mas porque este incidente hipotético é o cenário que a
implementação foi desenhada para prevenir: idempotência via constraint
`UNIQUE` (`SettlementService`), transação atômica cobrindo as duas
escritas, e teste de regressão específico para retry
(`ReceivableSettlementFlowIT`, cenário de "mesma chave duas vezes").

## 4. Prevenção sistêmica (sem virar burocracia)

O objetivo aqui é mudar o **sistema**, não pedir mais cuidado das pessoas
— "preste mais atenção" não escala e não é uma correção real.

- **Idempotência como contrato, não como boa prática opcional:** qualquer
  endpoint de escrita financeira nova exige `Idempotency-Key` — isso vira
  um item de checklist de PR objetivo e rápido de verificar (existe o
  header? existe constraint única correspondente no schema?), não uma
  reunião de arquitetura.
- **Golden cases e testes de concorrência como gate de CI, não como
  validação manual pós-deploy** — já implementado (`PricingEngineGoldenCasesTest`,
  `SettlementServiceConcurrencyIT`). O ponto de prevenção real é que essa
  suíte já existiria ANTES do próximo endpoint financeiro ser escrito, não
  depois de um incidente.
- **Reconciliação automática, não dependente do cedente avisar:** o gap
  mais sério exposto por este incidente não é só o bug — é que a detecção
  veio de fora (o cliente reclamando), não de dentro. Um job periódico
  (minutos, não dias) rodando a mesma query da seção 2.1 e alertando
  automaticamente sobre qualquer `receivable_id` com mais de uma
  liquidação fecha esse gap sem exigir disciplina humana nenhuma.
- **Nenhuma proibição de deploy sexta à tarde.** Proibir deploy num dia da
  semana é burocracia que não ataca a causa (o problema não foi "ser
  sexta", foi "não ter idempotência nem teste"). O que muda é: mudanças em
  caminho financeiro crítico exigem a suíte de regressão (golden cases +
  concorrência) passando como pré-requisito de merge, não uma janela de
  calendário proibida.
