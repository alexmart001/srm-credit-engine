package com.srm.creditengine.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Traduz um conflito de optimistic locking (@Version) detectado ao salvar o
 * Receivable durante a liquidacao: duas liquidacoes concorrentes do MESMO
 * recebivel foram tentadas e uma delas perdeu a corrida. O cliente deve
 * tratar isso como "tente novamente" (ou investigar, se o retry tambem
 * falhar - pode indicar liquidacao duplicada por engano, nao concorrencia).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConcurrentSettlementException extends RuntimeException {
    public ConcurrentSettlementException(Long receivableId, Throwable cause) {
        super("Conflito de concorrencia ao liquidar o recebivel " + receivableId +
                " - outra liquidacao foi processada simultaneamente. Tente novamente.", cause);
    }
}
