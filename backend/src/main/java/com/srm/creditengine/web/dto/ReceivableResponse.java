package com.srm.creditengine.web.dto;

import com.srm.creditengine.domain.Receivable;

public record ReceivableResponse(
        Long id,
        String cedente,
        String type,
        String faceValue,
        String faceCurrency,
        Integer termMonths,
        String paymentCurrency,
        String lockedFxRate,
        String status,
        String acquiredAt
) {
    public static ReceivableResponse from(Receivable r) {
        return new ReceivableResponse(
                r.getId(),
                r.getCedente(),
                r.getType().name(),
                r.getFaceValue().toPlainString(),
                r.getFaceCurrency().name(),
                r.getTermMonths(),
                r.getPaymentCurrency().name(),
                r.getLockedFxRate() != null ? r.getLockedFxRate().toPlainString() : null,
                r.getStatus().name(),
                r.getAcquiredAt().toString()
        );
    }
}
