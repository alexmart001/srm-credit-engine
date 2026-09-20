package com.srm.creditengine.web.dto;

import com.srm.creditengine.domain.Currency;
import com.srm.creditengine.domain.ReceivableType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateReceivableRequest(

        @NotBlank(message = "cedente e' obrigatorio")
        String cedente,

        @NotNull(message = "type e' obrigatorio")
        ReceivableType type,

        @NotNull(message = "faceValue e' obrigatorio")
        @DecimalMin(value = "0.01", message = "faceValue deve ser positivo")
        BigDecimal faceValue,

        @NotNull(message = "faceCurrency e' obrigatoria")
        Currency faceCurrency,

        @NotNull(message = "termMonths e' obrigatorio")
        @Min(value = 1, message = "termMonths deve ser um inteiro positivo (SPEC 1.1: sempre em meses)")
        Integer termMonths,

        @NotNull(message = "paymentCurrency e' obrigatoria")
        Currency paymentCurrency
) {
}
