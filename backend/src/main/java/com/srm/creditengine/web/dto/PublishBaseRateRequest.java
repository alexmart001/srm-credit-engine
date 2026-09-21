package com.srm.creditengine.web.dto;

import com.srm.creditengine.domain.Currency;
import com.srm.creditengine.domain.ReceivableType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PublishBaseRateRequest(

        @NotNull(message = "receivableType e' obrigatorio")
        ReceivableType receivableType,

        @NotNull(message = "currency e' obrigatoria")
        Currency currency,

        @NotNull(message = "rate e' obrigatorio")
        @DecimalMin(value = "0.0", message = "rate nao pode ser negativo")
        BigDecimal rate
) {
}
