# DECISIONS.md

Registro do que foi deliberadamente cortado ou simplificado nesta entrega,
e por que (item 10.3 do desafio).

## Cortes de escopo (v1)

- **Liquidação parcial**: fora de escopo. O domínio suporta apenas liquidação
  total (SPEC 1.5, confirmado com o negócio). Justificativa: reduz a máquina
  de estados do recebível a dois estados e simplifica a semântica de
  idempotência, que já é um requisito não-trivial por si só.

- **Validação de limites/concentração por cedente**: fora de escopo nesta v1
  (SPEC 1.7, confirmado com o negócio). Não há verificação de exposição
  máxima por cedente antes da liquidação.

- **Rate lock de câmbio negociado por operação individual**: todo câmbio e
  toda taxa base seguem a tabela de configuração vigente (fx_rates /
  base_rates); não há override manual por transação.

- **Timeout via `Future#get` em vez do `TimeLimiter` do Resilience4j**
  (`ResilientFxRateGateway`): o `TimeLimiter` anotado exige que o método
  retorne `CompletableFuture`, o que forçaria toda a cadeia de chamada
  (`FxRateAdminService`, potencialmente `ReceivableService`) a virar
  assíncrona só por causa desta dependência externa. Optamos por um
  timeout manual, síncrono, documentado no javadoc da classe.

## Ainda pendente (nível Staff/TL restante)

Conforme seção 6 do desafio, no nível Staff/Tech Lead o escopo de
implementação pode ser reduzido em favor de ADRs e do exercício de
incidente. O que falta nesta entrega:

- **Frontend funcional** — o painel do operador é só o skeleton do
  formulário; não há chamada real à API nem simulação em tempo real.
- **`AI_USAGE.md`** — engenharia da colaboração com IA (seção 7 do desafio).
- **ADRs** para as decisões mais difíceis (ex.: MariaDB vs. alternativas,
  rate lock na aquisição vs. na liquidação).
- **Design de alta escala (1M tx/min)** e **exercício de incidente (Anexo B)**
  — os itens mais "documento puro" do nível Staff/TL, deixados por último
  porque dependem menos de o código já existir e mais de tempo de escrita.

O que **já está pronto** e não deveria ser reconstruído: domínio,
persistência (Flyway), motor de precificação (golden cases), idempotência +
optimistic locking (`SettlementService`, testado com concorrência real),
controllers REST + tratamento de erro, observabilidade (logs estruturados +
métricas Micrometer), resiliência na integração de câmbio, CI (GitHub
Actions), `REVIEW.md` (Anexo A) e diagramas C4 (níveis 1 e 2,
`docs/c4-diagrams.md`).
