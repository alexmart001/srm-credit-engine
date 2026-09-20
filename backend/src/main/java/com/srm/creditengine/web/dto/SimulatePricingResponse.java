package com.srm.creditengine.web.dto;

import com.srm.creditengine.pricing.PricingResult;

public record SimulatePricingResponse(
        String presentValue,
        String currency,
        String discount,
        String baseRateUsed,
        String spreadUsed,
        String effectiveRateApplied,
        String fxRateUsed
) {
    public static SimulatePricingResponse from(java.math.BigDecimal faceValue, String currency, PricingResult r) {
        java.math.BigDecimal discount = faceValue.subtract(r.presentValueFaceCurrency());
        return new SimulatePricingResponse(
                r.presentValueSettlementCurrency().toPlainString(),
                currency,
                discount.toPlainString(),
                r.baseRateUsed().toPlainString(),
                r.spreadUsed().toPlainString(),
                r.effectiveRateApplied().toPlainString(),
                r.fxRateApplied() != null ? r.fxRateApplied().toPlainString() : null
        );
    }
}
