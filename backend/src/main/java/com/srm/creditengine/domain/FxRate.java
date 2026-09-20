package com.srm.creditengine.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cambio com vigencia temporal. Consultado no momento da AQUISICAO (SPEC 1.3)
 * para preencher Receivable.lockedFxRate - a liquidacao nao consulta mais
 * esta tabela, apenas reutiliza o valor ja travado.
 */
@Entity
@Table(name = "fx_rates")
public class FxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "currency_pair", nullable = false, length = 7)
    private String currencyPair; // ex: "USD/BRL"

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal rate;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    protected FxRate() {
        // JPA
    }

    public FxRate(String currencyPair, BigDecimal rate, Instant validFrom) {
        this.currencyPair = currencyPair;
        this.rate = rate;
        this.validFrom = validFrom;
    }

    /**
     * Encerra a vigencia desta taxa - chamado ao publicar uma taxa mais
     * recente para o mesmo par, para nunca deixar duas linhas "vigentes"
     * (validTo IS NULL) coexistindo para o mesmo par (ver
     * FxRateRepository#findEffectiveRate).
     */
    public void closeValidityAt(Instant to) {
        this.validTo = to;
    }

    public Long getId() { return id; }
    public String getCurrencyPair() { return currencyPair; }
    public BigDecimal getRate() { return rate; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidTo() { return validTo; }
}
