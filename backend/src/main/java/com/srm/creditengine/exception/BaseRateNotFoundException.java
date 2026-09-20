package com.srm.creditengine.exception;

import com.srm.creditengine.domain.Currency;
import com.srm.creditengine.domain.ReceivableType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class BaseRateNotFoundException extends RuntimeException {
    public BaseRateNotFoundException(ReceivableType type, Currency currency) {
        super("Nenhuma taxa base vigente cadastrada para tipo=" + type + ", moeda=" + currency);
    }
}
