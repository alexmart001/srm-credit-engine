# SRM Credit Engine

Motor de precificação e liquidação de recebíveis para o desafio técnico SRM
Asset (edição AI-native v2).

## Stack

- **Backend:** Java 21 + Spring Boot 3.5.16, `BigDecimal` em todo o pipeline de
  cálculo (nunca `float`/`double` para valores monetários).
- **Banco:** MariaDB 11, migrations via Flyway.
- **Frontend:** React + TypeScript (Vite).
- **Orquestração:** Docker Compose.

Justificativa da escolha: `BigDecimal` é nativo e idiomático em Java para
aritmética financeira decimal (item eliminatório do desafio a partir do
nível pleno é usar ponto flutuante binário para dinheiro). MariaDB foi
escolhido por familiaridade prévia da equipe com operação e tuning do banco.

## Como rodar

```bash
docker compose up --build
```

- Backend: http://localhost:8080 (Swagger UI em `/swagger-ui.html`)
- Frontend: http://localhost:5173
- MariaDB: porta 3306 (schema `srm_credit_engine`, aplicado automaticamente
  pelo Flyway na subida do backend)

## Rodando os testes

```bash
cd backend
mvn test
```

O teste `PricingEngineGoldenCasesTest` valida os três golden cases do
desafio (seção 4.3) ao centavo.

## Estrutura do projeto

```
srm-credit-engine/
├── backend/
│   ├── src/main/java/com/srm/creditengine/
│   │   ├── domain/       # Entidades JPA (Receivable, BaseRate, FxRate, Settlement)
│   │   ├── pricing/      # Strategy pattern + motor de cálculo (PricingEngine)
│   │   ├── repository/   # Spring Data JPA
│   │   ├── service/      # (a implementar: SettlementService com idempotência)
│   │   ├── web/          # (a implementar: controllers REST)
│   │   └── config/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/ # V1-V4, Flyway
│   └── src/test/java/.../pricing/PricingEngineGoldenCasesTest.java
├── frontend/             # React + TS + Vite (skeleton do painel do operador)
├── docs/adr/             # ADRs (nível Staff/TL)
├── SPEC.md               # Premissas e decisões (Fase 0 do desafio)
├── DECISIONS.md          # Cortes de escopo e justificativas
├── REVIEW.md             # (a escrever: code review do Anexo A)
├── AI_USAGE.md           # (a escrever: engenharia da colaboração com IA)
└── docker-compose.yml
```

## Documentos do desafio

- [`SPEC.md`](./SPEC.md) — premissas adotadas para as ambiguidades do
  enunciado, decisões de precisão numérica e critérios de aceite.
- [`DECISIONS.md`](./DECISIONS.md) — o que foi cortado/simplificado e por quê.

## Status atual

✅ Domínio e persistência (entidades JPA + migrations Flyway)
✅ Motor de precificação (Strategy pattern) com golden cases validados
✅ `SettlementService` — idempotência (constraint única) + optimistic locking (`@Version`)
✅ Controllers REST: `POST /receivables` (aquisição + rate lock de câmbio), `GET /receivables/{id}`,
   `POST /receivables/{id}/settlements` (liquidação idempotente), `GET /settlements/{id}`,
   `GET /settlements` (extrato paginado — item 4.1.6: filtro por cedente, moeda e período)
✅ `GlobalExceptionHandler` — erro sempre com status semântico e corpo consistente (nunca 200 OK)
✅ SPEC.md e DECISIONS.md
✅ Teste de optimistic locking com conflito concorrente real (dois threads)
✅ Teste end-to-end via HTTP (MockMvc) cobrindo aquisição → liquidação → idempotência → cross-currency
✅ REVIEW.md (Anexo A) — 12 achados ordenados por severidade
✅ CI (GitHub Actions) — build + testes + checkstyle (backend), build (frontend)
✅ Observabilidade — logs estruturados (SLF4J fluente + Spring Boot ECS) e métricas de negócio
   (`srm.settlements.total`, `srm.settlement.duration`, `srm.pricing.duration` em `/actuator/prometheus`)
⬜ Frontend funcional (simulação em tempo real) — só o skeleton do formulário existe
⬜ Resiliência (timeout/retry/circuit breaker na integração de câmbio)
⬜ AI_USAGE.md, ADRs, diagrama C4
⬜ Endpoint de cadastro de câmbio/taxa base (hoje só populados via repositório/seed — sem rota admin)

## Observabilidade

- **Logs estruturados:** `SettlementService` usa a API fluente do SLF4J
  (`log.atInfo().addKeyValue(...)`). Localmente aparecem como texto legível;
  no `docker-compose` (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`), o próprio
  Spring Boot 3.4+ converte para JSON (Elastic Common Schema) sem
  dependência extra.
- **Métricas de negócio (Micrometer → `/actuator/prometheus`):**
  - `srm_settlements_total{outcome=...}` — contador por desfecho
    (`success`, `idempotent_replay`, `idempotency_race_resolved`,
    `already_settled`, `concurrency_conflict`, `receivable_not_found`,
    `base_rate_not_found`)
  - `srm_settlement_duration_seconds{outcome=...}` — latência total por desfecho
  - `srm_pricing_duration_seconds` — latência isolada do `PricingEngine`
    (exemplo citado no item 6 do desafio: "latência do motor")

## CI

`.github/workflows/ci.yml` roda em todo push/PR para `main`:
- **backend:** `mvn compile` → `mvn test` (golden cases + `SettlementService`
  + concorrência + fluxo HTTP) → `checkstyle:checkstyle` (modo relatório,
  não bloqueia o build ainda — ver comentário no `pom.xml`)
- **frontend:** `npm ci` → `npm run build` (tsc + vite)

Nota: `npm audit` acusa uma vulnerabilidade moderada em `esbuild` (só afeta
o dev server do Vite, não o build de produção); resolver exigiria upgrade
major do Vite, deixado como item futuro em vez de feito às cegas.

## Observações importantes para a defesa

- `mvn compile` e `mvn test` rodados localmente (Java 21 / Spring Boot 3.5.16) sem erros -
  cobre golden cases, `SettlementService` (idempotência + optimistic locking) e o fluxo HTTP
  end-to-end (incluindo o caso cross-currency C3).
- O `GET /settlements` usa JPQL com `JOIN ... ON` (Settlement não tem relacionamento JPA mapeado
  para Receivable, de propósito — é uma entidade imutável e "burra"). Se o volume justificar,
  trocar por `@Query(nativeQuery = true)` é o próximo passo natural (diferencial pleno+, item 4.1.6).
- A recuperação após corrida de idempotência (`DataIntegrityViolationException`) reaproveita a
  mesma transação — seguro no MariaDB/H2 (falha de constraint não aborta a transação inteira),
  mas quebraria no PostgreSQL (que aborta a transação em qualquer erro). Documentado no código.
