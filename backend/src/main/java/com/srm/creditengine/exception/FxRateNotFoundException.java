package com.srm.creditengine.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class FxRateNotFoundException extends RuntimeException {
    public FxRateNotFoundException(String currencyPair) {
        super("Nenhuma taxa de cambio vigente cadastrada para o par: " + currencyPair);
    }
}
