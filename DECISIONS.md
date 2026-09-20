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

Um único item do desafio ainda não foi tratado:

- **Exercício de incidente (Anexo B)** — post-mortem por escrito de um
  incidente hipotético de liquidações duplicadas.

Deixado por último porque o `REVIEW.md` (seção 3 deste documento) já
adianta boa parte da causa raiz, o que exige cuidado para não fazer do
post-mortem uma repetição do review em vez de um exercício de linha do
tempo, contenção e prevenção sistêmica.

O que **já está pronto** e não deveria ser reconstruído: domínio,
persistência (Flyway), motor de precificação (golden cases), idempotência +
optimistic locking (`SettlementService`, testado com concorrência real),
controllers REST + tratamento de erro, endpoint de simulação
(`POST /pricing/simulate`), frontend funcional, observabilidade (logs
estruturados + métricas Micrometer), resiliência na integração de câmbio,
CI (GitHub Actions), `REVIEW.md` (Anexo A), `AI_USAGE.md`, ADRs
(`docs/adr/`), diagramas C4 (`docs/c4-diagrams.md`) e o design de alta
escala (`docs/scale-design.md`).
