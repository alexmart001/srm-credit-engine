package com.srm.creditengine.exception;

/**
 * Lancada pelo provedor externo (mockado) de cambio para simular
 * indisponibilidade (timeout, erro 5xx, conexao recusada). NAO tem
 * @ResponseStatus porque nunca deveria vazar ate' o cliente HTTP - e'
 * capturada e tratada dentro de ResilientFxRateGateway/FxRateAdminService,
 * que degradam graciosamente para a ultima taxa conhecida.
 */
public class FxProviderUnavailableException extends RuntimeException {
    public FxProviderUnavailableException(String message) {
        super(message);
    }

    public FxProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
