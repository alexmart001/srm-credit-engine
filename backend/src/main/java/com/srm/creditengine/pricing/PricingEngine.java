package com.srm.creditengine.pricing;

import com.srm.creditengine.domain.ReceivableType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Motor de precificacao. Formula base (item 4.1.2 do desafio):
 *
 *     VP = Valor de Face / (1 + Taxa Base + Spread) ^ Prazo
 *
 * Decisoes de precisao (SPEC secao 3):
 *   - Toda a aritmetica intermediaria usa BigDecimal com MathContext de alta
 *     precisao (34 digitos, HALF_EVEN) - nenhum arredondamento ate o passo final.
 *   - O arredondamento para 2 casas (half-even) acontece SOMENTE no valor
 *     final apresentado/persistido (SPEC 1.4).
 *   - Prazo (n) e' sempre inteiro (SPEC 1.1), entao BigDecimal#pow(int) e'
 *     suficiente - nao ha' necessidade de exponenciacao fracionaria.
 *
 * SPEC 1.6: se (taxaBase + spread) for negativo, a taxa aplicada ao calculo
 * e' zero, mas a taxa "raw" (pode ser negativa) tambem e' retornada para
 * auditoria - quem decide o que persistir e' o chamador (SettlementService).
 *
 * Cross-currency (SPEC 1.3): o VP e' calculado e arredondado na moeda de
 * face (BRL) primeiro; so' depois se divide pela taxa de cambio TRAVADA NA
 * AQUISICAO (nao a vigente no momento do calculo) - o valor da taxa e'
 * responsabilidade do chamador informar (ja vem de Receivable.lockedFxRate).
 */
@Component
public class PricingEngine {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    private static final MathContext HIGH_PRECISION = new MathContext(34, ROUNDING);

    private final SpreadStrategyFactory spreadStrategyFactory;

    public PricingEngine(SpreadStrategyFactory spreadStrategyFactory) {
        this.spreadStrategyFactory = spreadStrategyFactory;
    }

    /**
     * @param faceValue   valor de face do recebivel (na moeda de face, ex. BRL)
     * @param type        tipo do recebivel (define o spread via Strategy)
     * @param termMonths  prazo em meses inteiros (SPEC 1.1)
     * @param baseRate    taxa base vigente para (tipo, moeda) - buscada pelo chamador (SPEC 1.2)
     * @param fxRateLocked taxa de cambio travada na aquisicao, ou null se nao for cross-currency (SPEC 1.3)
     */
    public PricingResult price(BigDecimal faceValue, ReceivableType type, int termMonths,
                                BigDecimal baseRate, BigDecimal fxRateLocked) {

        if (termMonths <= 0) {
            throw new IllegalArgumentException("Prazo deve ser um numero inteiro positivo de meses");
        }

        SpreadStrategy spreadStrategy = spreadStrategyFactory.resolve(type);
        BigDecimal spread = spreadStrategy.getMonthlySpread();

        // SPEC 1.6: taxa efetiva "crua" pode ser negativa; a aplicada tem piso em zero.
        BigDecimal effectiveRateRaw = baseRate.add(spread);
        BigDecimal effectiveRateApplied = effectiveRateRaw.max(BigDecimal.ZERO);

        BigDecimal onePlusI = BigDecimal.ONE.add(effectiveRateApplied);
        BigDecimal denominator = onePlusI.pow(termMonths, HIGH_PRECISION);

        BigDecimal presentValueRaw = faceValue.divide(denominator, HIGH_PRECISION);
        BigDecimal presentValueFaceCurrency = presentValueRaw.setScale(SCALE, ROUNDING);

        BigDecimal presentValueSettlementCurrency = presentValueFaceCurrency;
        BigDecimal fxRateApplied = null;

        if (fxRateLocked != null) {
            // SPEC: "converte-se o valor presente em BRL ja' arredondado pela taxa informada"
            fxRateApplied = fxRateLocked;
            BigDecimal convertedRaw = presentValueFaceCurrency.divide(fxRateLocked, HIGH_PRECISION);
            presentValueSettlementCurrency = convertedRaw.setScale(SCALE, ROUNDING);
        }

        return new PricingResult(
                baseRate,
                spread,
                effectiveRateRaw,
                effectiveRateApplied,
                presentValueFaceCurrency,
                presentValueSettlementCurrency,
                fxRateApplied
        );
    }
}
