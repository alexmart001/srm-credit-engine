package com.srm.creditengine.web.dto;

import com.srm.creditengine.domain.Currency;
import com.srm.creditengine.domain.ReceivableType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Simulacao "somente leitura": calcula o valor liquido SEM criar nenhum
 * recebivel nem travar cambio (isso so' acontece de verdade em
 * POST /receivables). Usada pelo painel do operador para a simulacao em
 * tempo real (item 4.2.1 do desafio) enquanto o operador ainda esta
 * digitando/ajustando os campos.
 */
public record SimulatePricingRequest(

        @NotNull(message = "type e' obrigatorio")
        ReceivableType type,

        @NotNull(message = "faceValue e' obrigatorio")
        @DecimalMin(value = "0.01", message = "faceValue deve ser positivo")
        BigDecimal faceValue,

        @NotNull(message = "faceCurrency e' obrigatoria")
        Currency faceCurrency,

        @NotNull(message = "termMonths e' obrigatorio")
        @Min(value = 1, message = "termMonths deve ser um inteiro positivo")
        Integer termMonths,

        @NotNull(message = "paymentCurrency e' obrigatoria")
        Currency paymentCurrency
) {
}
