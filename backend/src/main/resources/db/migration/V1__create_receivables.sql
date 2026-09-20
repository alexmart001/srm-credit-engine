-- Recebiveis. Prazo sempre em meses inteiros (SPEC 1.1).
-- locked_fx_rate reflete o rate lock na aquisicao (SPEC 1.3): so e' preenchido
-- quando payment_currency difere da moeda de face (cross-currency).
CREATE TABLE receivables (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    cedente             VARCHAR(255)    NOT NULL,
    type                VARCHAR(30)     NOT NULL,   -- DUPLICATA_MERCANTIL | CHEQUE_PRE_DATADO
    face_value          DECIMAL(18,2)   NOT NULL,
    face_currency       VARCHAR(3)      NOT NULL DEFAULT 'BRL',
    term_months         INT             NOT NULL,
    payment_currency    VARCHAR(3)      NOT NULL,   -- BRL | USD
    locked_fx_rate      DECIMAL(18,8)   NULL,       -- so' preenchido se payment_currency <> face_currency
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDENTE', -- PENDENTE | LIQUIDADO
    acquired_at         DATETIME(6)     NOT NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             BIGINT          NOT NULL DEFAULT 0, -- optimistic locking (nivel senior)

    CONSTRAINT chk_receivables_term_months CHECK (term_months > 0),
    CONSTRAINT chk_receivables_face_value CHECK (face_value > 0)
);

CREATE INDEX idx_receivables_cedente ON receivables (cedente);
CREATE INDEX idx_receivables_status ON receivables (status);
