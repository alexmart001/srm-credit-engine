-- Registro IMUTAVEL de liquidacao (requisito de auditabilidade, item 4.1.4
-- do desafio). Nunca sofre UPDATE apos criado - qualquer correcao gera um
-- novo registro de estorno/ajuste, nunca uma alteracao in-place.
--
-- effective_rate_raw / effective_rate_applied refletem SPEC 1.6: taxa
-- efetiva pode ser negativa (raw), mas o piso aplicado ao calculo e' zero.
CREATE TABLE settlements (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    receivable_id           BIGINT          NOT NULL,
    idempotency_key         VARCHAR(100)    NOT NULL,
    present_value           DECIMAL(18,2)   NOT NULL,
    settlement_currency     VARCHAR(3)      NOT NULL,
    base_rate_used          DECIMAL(18,8)   NOT NULL,
    spread_used             DECIMAL(18,8)   NOT NULL,
    effective_rate_raw      DECIMAL(18,8)   NOT NULL,  -- pode ser negativo
    effective_rate_applied  DECIMAL(18,8)   NOT NULL,  -- sempre >= 0
    fx_rate_used            DECIMAL(18,8)   NULL,       -- copia do locked_fx_rate, se cross-currency
    settled_at              DATETIME(6)     NOT NULL,
    created_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_settlements_receivable FOREIGN KEY (receivable_id) REFERENCES receivables (id),
    CONSTRAINT uq_settlements_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_settlements_receivable ON settlements (receivable_id);
CREATE INDEX idx_settlements_settled_at ON settlements (settled_at);
