# Diagramas C4 — SRM Credit Engine

Níveis 1 (Contexto) e 2 (Contêineres), conforme item 6 do desafio (nível
sênior). Ambos em Mermaid — renderizam nativamente no GitHub, sem
ferramenta externa.

## Nível 1 — Contexto do Sistema

Quem usa o sistema e com quais sistemas externos ele conversa.

```mermaid
C4Context
    title Contexto do Sistema — SRM Credit Engine

    Person(operador, "Operador de Mesa", "Precifica e liquida recebiveis do fundo")

    System(srm, "SRM Credit Engine", "Precifica recebiveis (duplicatas, cheques) e registra liquidacoes multimoedas com auditoria imutavel")

    System_Ext(provedorCambio, "Provedor de Cambio", "Fonte externa (mockada) de cotacoes de mercado - ex.: USD/BRL")

    Rel(operador, srm, "Simula deságio, registra aquisições e liquidações", "HTTPS")
    Rel(srm, provedorCambio, "Atualiza a taxa de câmbio local (administrativo, não bloqueia liquidação)", "HTTPS — timeout + retry + circuit breaker")
```

**Decisão de arquitetura que este nível já revela:** a seta entre o SRM
Credit Engine e o Provedor de Câmbio é rotulada "administrativo, não
bloqueia liquidação" de propósito — é a materialização visual da decisão do
SPEC 1.3 (câmbio travado na aquisição) e da seção de resiliência: nenhuma
operação financeira síncrona (aquisição ou liquidação) depende da
disponibilidade desse sistema externo em tempo real.

## Nível 2 — Contêineres

Como o SRM Credit Engine se decompõe internamente.

```mermaid
C4Container
    title Contêineres — SRM Credit Engine

    Person(operador, "Operador de Mesa", "Precifica e liquida recebiveis")

    System_Boundary(srm, "SRM Credit Engine") {
        Container(spa, "Painel do Operador", "React 18 + TypeScript + Vite", "Simulação de deságio em tempo real, grid de transações")
        Container(api, "API de Precificação e Liquidação", "Java 21 + Spring Boot 3.5", "Motor de cálculo (Strategy), idempotência, optimistic locking, regras de negócio, endpoints REST")
        ContainerDb(db, "Banco de Dados", "MariaDB 11", "Recebíveis, taxas (base/câmbio) com vigência temporal, liquidações (registro imutável)")
    }

    System_Ext(provedorCambio, "Provedor de Câmbio", "Integração mockada — ExternalFxRateProvider")

    Rel(operador, spa, "Usa", "HTTPS")
    Rel(spa, api, "Aquisição, liquidação, extrato", "JSON / HTTPS")
    Rel(api, db, "Lê e escreve (JPA/Flyway)", "JDBC")
    Rel(api, provedorCambio, "Atualiza taxa (endpoint admin/manual, ResilientFxRateGateway)", "HTTPS — timeout + retry + circuit breaker")
```

**Fronteiras de responsabilidade que valem destacar na defesa:**

- O **Painel do Operador** nunca fala diretamente com o Provedor de Câmbio
  nem com o banco — tudo passa pela API. O contêiner de API é o único ponto
  de acesso a dados e a integrações externas (nenhuma lógica de negócio ou
  acesso a dados no frontend).
- Dentro da API, `ReceivableService` (aquisição/liquidação) só fala com o
  Banco de Dados — nunca com o Provedor de Câmbio diretamente. Só
  `FxRateAdminService`/`ResilientFxRateGateway` (fluxo administrativo,
  fora do caminho crítico de uma transação financeira) fala com o
  provedor externo. Essa separação é o que torna a resposta a "o que
  acontece se o provedor de câmbio cair no meio de uma liquidação?"
  trivial: nada acontece, porque a liquidação nunca o chama.
