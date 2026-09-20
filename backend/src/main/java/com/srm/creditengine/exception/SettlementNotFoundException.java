package com.srm.creditengine.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class SettlementNotFoundException extends RuntimeException {
    public SettlementNotFoundException(Long id) {
        super("Liquidacao nao encontrada: id=" + id);
    }
}
