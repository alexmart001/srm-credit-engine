# ADR 0001 — Banco de dados relacional (MariaDB), não NoSQL

## Status
Aceito

## Contexto
O motor de precificação exige ACID de verdade: uma liquidação escreve em
duas tabelas (`settlements`, `receivables`) e nenhuma das duas pode ficar
"pela metade" (item 4.1.3 do desafio). Idempotência (constraint única) e
optimistic locking (`@Version`) também são mecanismos nativos de bancos
relacionais com transações multi-linha reais.

## Decisão
MariaDB, com Flyway controlando o schema.

## Alternativas consideradas
- **MongoDB / documento:** transações multi-documento existem desde a
  versão 4.0, mas são mais caras em latência e menos idiomáticas para o
  padrão "duas tabelas, uma transação, um lock otimista" que este domínio
  pede o tempo todo. Não traria benefício de escala aqui — o gargalo deste
  sistema é correção transacional, não volume de escrita não-estruturada.
- **PostgreSQL:** candidato igualmente válido (e em alguns aspectos mais
  rico: `SERIALIZABLE` isolation, `EXCLUDE` constraints). Não escolhido por
  familiaridade prévia da equipe com operação/tuning de MariaDB — decisão
  de custo operacional, não de superioridade técnica de um sobre o outro.
- **NoSQL de qualquer tipo para o registro de liquidação:** o próprio
  requisito de auditoria imutável com múltiplas colunas correlacionadas
  (taxa base, spread, taxa efetiva real/aplicada, câmbio) é relacional por
  natureza — normalizar isso em um documento não traria vantagem, só
  reimplementaria índices e constraints que o banco relacional já dá de
  graça.

## Consequências
- Positivo: `UNIQUE` constraint resolve idempotência sem código adicional;
  `@Version` do JPA resolve optimistic locking sem biblioteca extra.
- Negativo: escala horizontal de escrita é mais trabalhosa que em um banco
  distribuído nativo — aceitável no volume deste domínio (liquidações de
  um fundo, não uma feed de eventos de altíssima frequência). Se o volume
  um dia exigir sharding, é o gatilho para revisitar esta decisão (ver
  também: o documento de design para 1M tx/min, ainda pendente nesta
  entrega).
