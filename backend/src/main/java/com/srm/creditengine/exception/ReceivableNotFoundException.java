package com.srm.creditengine.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ReceivableNotFoundException extends RuntimeException {
    public ReceivableNotFoundException(Long receivableId) {
        super("Recebivel nao encontrado: id=" + receivableId);
    }
}
