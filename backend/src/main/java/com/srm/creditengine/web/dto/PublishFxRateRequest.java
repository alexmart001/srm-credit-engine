package com.srm.creditengine.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PublishFxRateRequest(

        @NotBlank(message = "currencyPair e' obrigatorio (ex.: 'USD/BRL')")
        String currencyPair,

        @NotNull(message = "rate e' obrigatorio")
        @DecimalMin(value = "0.00000001", message = "rate deve ser positivo")
        BigDecimal rate
) {
}
