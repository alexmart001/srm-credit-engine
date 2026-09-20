package com.srm.creditengine.web.dto;

import com.srm.creditengine.domain.FxRate;

public record FxRateResponse(
        Long id,
        String currencyPair,
        String rate,
        String validFrom,
        String validTo
) {
    public static FxRateResponse from(FxRate fxRate) {
        return new FxRateResponse(
                fxRate.getId(),
                fxRate.getCurrencyPair(),
                fxRate.getRate().toPlainString(),
                fxRate.getValidFrom().toString(),
                fxRate.getValidTo() != null ? fxRate.getValidTo().toString() : null
        );
    }
}
