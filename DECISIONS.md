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

## Escopo reduzido conscientemente para investir em decisão (nível Staff/TL)

Conforme seção 6 do desafio, no nível Staff/Tech Lead o escopo de
implementação pode ser reduzido em favor de ADRs, design de escala e o
exercício de incidente. Itens abaixo foram *implementados como skeleton*
(estrutura + contrato definidos) mas não como funcionalidade completa nesta
primeira entrega:

- Camada de API REST completa (controllers) — o domínio, a persistência e o
  motor de precificação estão implementados e testados (golden cases); os
  endpoints HTTP (`POST /receivables`, `POST /settlements`,
  `GET /settlements`) ainda não foram escritos nesta rodada.
- Idempotência do endpoint de liquidação — a constraint UNIQUE em
  `settlements.idempotency_key` já está no schema (V4), mas o service que a
  usa (`SettlementService`) ainda não foi implementado.
- Optimistic locking — o campo `@Version` já existe em `Receivable`, mas o
  teste que demonstra o conflito concorrente ainda não foi escrito.
- Observabilidade, resiliência (circuit breaker no câmbio) e CI — planejados,
  não implementados nesta rodada.

Justificativa: priorizar a corretude do núcleo de cálculo (maior peso da
rubrica em conjunto com domínio do negócio) e deixar a superfície HTTP e a
infraestrutura operacional para as próximas iterações, documentadas aqui em
vez de entregues como código apressado.
