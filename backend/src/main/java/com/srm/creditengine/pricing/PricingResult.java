package com.srm.creditengine.pricing;

import java.math.BigDecimal;

/**
 * Resultado imutavel de uma precificacao. Carrega todos os componentes
 * intermediarios (nao so' o valor final) porque o registro de liquidacao
 * (Settlement) precisa persistir base_rate_used, spread_used,
 * effective_rate_raw e effective_rate_applied para auditoria (SPEC 1.6).
 *
 * presentValueSettlementCurrency: valor final, ja' arredondado, na moeda
 * de liquidacao (BRL ou USD, conforme Receivable.paymentCurrency).
 */
public record PricingResult(
        BigDecimal baseRateUsed,
        BigDecimal spreadUsed,
        BigDecimal effectiveRateRaw,
        BigDecimal effectiveRateApplied,
        BigDecimal presentValueFaceCurrency,          // VP arredondado na moeda de face (ex: BRL)
        BigDecimal presentValueSettlementCurrency,     // VP arredondado na moeda de liquidacao (pode ser igual ao de face)
        BigDecimal fxRateApplied                       // null se nao for cross-currency
) {
}
