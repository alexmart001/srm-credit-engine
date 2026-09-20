package com.srm.creditengine.web.dto;

import com.srm.creditengine.domain.Settlement;

/**
 * Valores monetarios e taxas trafegam como STRING (SPEC secao 3) - nunca
 * como numero JSON, para nao arriscar perda de precisao decimal em
 * clientes que desserializam para double/float (ex.: JavaScript).
 */
public record SettlementResponse(
        Long id,
        Long receivableId,
        String presentValue,
        String currency,
        String baseRateUsed,
        String spreadUsed,
        String effectiveRateRaw,
        String effectiveRateApplied,
        String fxRateUsed,
        String settledAt
) {
    public static SettlementResponse from(Settlement s) {
        return new SettlementResponse(
                s.getId(),
                s.getReceivableId(),
                s.getPresentValue().toPlainString(),
                s.getSettlementCurrency().name(),
                s.getBaseRateUsed().toPlainString(),
                s.getSpreadUsed().toPlainString(),
                s.getEffectiveRateRaw().toPlainString(),
                s.getEffectiveRateApplied().toPlainString(),
                s.getFxRateUsed() != null ? s.getFxRateUsed().toPlainString() : null,
                s.getSettledAt().toString()
        );
    }
}
