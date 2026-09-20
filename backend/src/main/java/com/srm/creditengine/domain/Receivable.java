package com.srm.creditengine.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * SPEC 1.1: term em meses inteiros.
 * SPEC 1.3: lockedFxRate e' gravado na AQUISICAO (rate lock), nao na liquidacao.
 *           So' e' preenchido quando paymentCurrency != faceCurrency.
 * SPEC 1.5: sem estado de liquidacao parcial - status e' binario.
 * version: optimistic locking (nivel senior) - protege contra duas liquidacoes
 *          concorrentes do mesmo recebivel.
 */
@Entity
@Table(name = "receivables")
public class Receivable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String cedente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReceivableType type;

    @Column(name = "face_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal faceValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "face_currency", nullable = false, length = 3)
    private Currency faceCurrency = Currency.BRL;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_currency", nullable = false, length = 3)
    private Currency paymentCurrency;

    /** Taxa de cambio travada na aquisicao (SPEC 1.3). Nulo se nao for cross-currency. */
    @Column(name = "locked_fx_rate", precision = 18, scale = 8)
    private BigDecimal lockedFxRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceivableStatus status = ReceivableStatus.PENDENTE;

    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Receivable() {
        // JPA
    }

    public Receivable(String cedente, ReceivableType type, BigDecimal faceValue,
                       Currency faceCurrency, Integer termMonths, Currency paymentCurrency,
                       BigDecimal lockedFxRate, Instant acquiredAt) {
        this.cedente = cedente;
        this.type = type;
        this.faceValue = faceValue;
        this.faceCurrency = faceCurrency;
        this.termMonths = termMonths;
        this.paymentCurrency = paymentCurrency;
        this.lockedFxRate = lockedFxRate;
        this.acquiredAt = acquiredAt;
        this.status = ReceivableStatus.PENDENTE;
    }

    public boolean isCrossCurrency() {
        return faceCurrency != paymentCurrency;
    }

    public void markAsSettled() {
        this.status = ReceivableStatus.LIQUIDADO;
    }

    // getters

    public Long getId() { return id; }
    public String getCedente() { return cedente; }
    public ReceivableType getType() { return type; }
    public BigDecimal getFaceValue() { return faceValue; }
    public Currency getFaceCurrency() { return faceCurrency; }
    public Integer getTermMonths() { return termMonths; }
    public Currency getPaymentCurrency() { return paymentCurrency; }
    public BigDecimal getLockedFxRate() { return lockedFxRate; }
    public ReceivableStatus getStatus() { return status; }
    public Instant getAcquiredAt() { return acquiredAt; }
    public Long getVersion() { return version; }
}
