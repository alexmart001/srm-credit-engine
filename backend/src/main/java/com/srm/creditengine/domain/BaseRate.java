package com.srm.creditengine.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * SPEC 1.2: taxa base multi-dimensional - varia por (tipo de recebivel x moeda),
 * com vigencia temporal, mesmo padrao usado para FxRate.
 */
@Entity
@Table(name = "base_rates")
public class BaseRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "receivable_type", nullable = false, length = 30)
    private ReceivableType receivableType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal rate;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    protected BaseRate() {
        // JPA
    }

    public BaseRate(ReceivableType receivableType, Currency currency, BigDecimal rate, Instant validFrom) {
        this.receivableType = receivableType;
        this.currency = currency;
        this.rate = rate;
        this.validFrom = validFrom;
    }

    public Long getId() { return id; }
    public ReceivableType getReceivableType() { return receivableType; }
    public Currency getCurrency() { return currency; }
    public BigDecimal getRate() { return rate; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidTo() { return validTo; }
}
