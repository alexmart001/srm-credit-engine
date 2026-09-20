package com.srm.creditengine.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Lancada quando se tenta liquidar (com uma NOVA idempotency key) um
 * recebivel cujo status ja e' LIQUIDADO. Nao confundir com o caminho de
 * idempotencia (mesma idempotency key repetida) - esse retorna o resultado
 * existente em vez de lancar excecao (ver SettlementService#settle).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ReceivableAlreadySettledException extends RuntimeException {
    public ReceivableAlreadySettledException(Long receivableId) {
        super("Recebivel " + receivableId + " ja foi liquidado - liquidacao total nao pode ser repetida");
    }
}
