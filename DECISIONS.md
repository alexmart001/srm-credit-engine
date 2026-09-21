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

## Status da entrega

Todos os itens do desafio (Fases 0–3, Sênior e Staff/TL) estão tratados:
domínio, persistência (Flyway), motor de precificação (golden cases),
idempotência + optimistic locking (`SettlementService`, testado com
concorrência real), controllers REST + tratamento de erro, endpoint de
simulação (`POST /pricing/simulate`), frontend funcional, observabilidade
(logs estruturados + métricas Micrometer), resiliência na integração de
câmbio, CI (GitHub Actions), `REVIEW.md` (Anexo A), `AI_USAGE.md`, ADRs
(`docs/adr/`), diagramas C4 (`docs/c4-diagrams.md`), design de alta escala
(`docs/scale-design.md`) e post-mortem do Anexo B
(`docs/postmortem-anexo-b.md`).

Os cortes de escopo desta v1 estão listados no topo deste documento
(liquidação parcial, validação de limites por cedente, override manual de
taxa por operação individual) — nenhum é uma omissão não intencional.
