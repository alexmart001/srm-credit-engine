package com.srm.creditengine.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Registro IMUTAVEL de liquidacao (item 4.1.4 do desafio). Nao ha metodos
 * setters nem operacao de update no repositorio - uma vez persistido, o
 * unico jeito de "corrigir" e' criar um novo registro de ajuste.
 *
 * effectiveRateRaw / effectiveRateApplied refletem SPEC 1.6.
 * idempotencyKey e' a chave de idempotencia do endpoint de liquidacao
 * (constraint UNIQUE no banco - ver V4__create_settlements.sql).
 */
@Entity
@Table(name = "settlements")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receivable_id", nullable = false)
    private Long receivableId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "present_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal presentValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_currency", nullable = false, length = 3)
    private Currency settlementCurrency;

    @Column(name = "base_rate_used", nullable = false, precision = 18, scale = 8)
    private BigDecimal baseRateUsed;

    @Column(name = "spread_used", nullable = false, precision = 18, scale = 8)
    private BigDecimal spreadUsed;

    @Column(name = "effective_rate_raw", nullable = false, precision = 18, scale = 8)
    private BigDecimal effectiveRateRaw;

    @Column(name = "effective_rate_applied", nullable = false, precision = 18, scale = 8)
    private BigDecimal effectiveRateApplied;

    @Column(name = "fx_rate_used", precision = 18, scale = 8)
    private BigDecimal fxRateUsed;

    @Column(name = "settled_at", nullable = false)
    private Instant settledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Settlement() {
        // JPA
    }

    public Settlement(Long receivableId, String idempotencyKey, BigDecimal presentValue,
                       Currency settlementCurrency, BigDecimal baseRateUsed, BigDecimal spreadUsed,
                       BigDecimal effectiveRateRaw, BigDecimal effectiveRateApplied,
                       BigDecimal fxRateUsed, Instant settledAt) {
        this.receivableId = receivableId;
        this.idempotencyKey = idempotencyKey;
        this.presentValue = presentValue;
        this.settlementCurrency = settlementCurrency;
        this.baseRateUsed = baseRateUsed;
        this.spreadUsed = spreadUsed;
        this.effectiveRateRaw = effectiveRateRaw;
        this.effectiveRateApplied = effectiveRateApplied;
        this.fxRateUsed = fxRateUsed;
        this.settledAt = settledAt;
        // Mesma correcao aplicada em Receivable: sem isto, o Hibernate
        // envia NULL explicito para created_at, violando o NOT NULL da
        // coluna no MariaDB.
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getReceivableId() { return receivableId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public BigDecimal getPresentValue() { return presentValue; }
    public Currency getSettlementCurrency() { return settlementCurrency; }
    public BigDecimal getBaseRateUsed() { return baseRateUsed; }
    public BigDecimal getSpreadUsed() { return spreadUsed; }
    public BigDecimal getEffectiveRateRaw() { return effectiveRateRaw; }
    public BigDecimal getEffectiveRateApplied() { return effectiveRateApplied; }
    public BigDecimal getFxRateUsed() { return fxRateUsed; }
    public Instant getSettledAt() { return settledAt; }
}
