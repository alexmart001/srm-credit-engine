-- Cambio com vigencia temporal. Usado no momento da AQUISICAO (SPEC 1.3),
-- nao no momento da liquidacao - a liquidacao reutiliza o locked_fx_rate
-- ja gravado em receivables.
CREATE TABLE fx_rates (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    currency_pair       VARCHAR(7)      NOT NULL,   -- ex: 'USD/BRL'
    rate                DECIMAL(18,8)   NOT NULL,
    valid_from          DATETIME(6)     NOT NULL,
    valid_to            DATETIME(6)     NULL,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_fx_rates_lookup ON fx_rates (currency_pair, valid_from);
