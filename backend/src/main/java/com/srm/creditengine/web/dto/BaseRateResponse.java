package com.srm.creditengine.web.dto;

import com.srm.creditengine.domain.BaseRate;

public record BaseRateResponse(
        Long id,
        String receivableType,
        String currency,
        String rate,
        String validFrom,
        String validTo
) {
    public static BaseRateResponse from(BaseRate baseRate) {
        return new BaseRateResponse(
                baseRate.getId(),
                baseRate.getReceivableType().name(),
                baseRate.getCurrency().name(),
                baseRate.getRate().toPlainString(),
                baseRate.getValidFrom().toString(),
                baseRate.getValidTo() != null ? baseRate.getValidTo().toString() : null
        );
    }
}
