-- Taxa base multi-dimensional (SPEC 1.2): varia por tipo de recebivel e moeda,
-- com vigencia temporal - mesmo mecanismo usado para cambio (V3).
CREATE TABLE base_rates (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    receivable_type     VARCHAR(30)     NOT NULL,
    currency            VARCHAR(3)      NOT NULL,
    rate                DECIMAL(18,8)   NOT NULL,   -- ex: 0.01000000 = 1% a.m.
    valid_from          DATETIME(6)     NOT NULL,
    valid_to            DATETIME(6)     NULL,       -- NULL = ainda vigente
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_base_rates_lookup ON base_rates (receivable_type, currency, valid_from);
