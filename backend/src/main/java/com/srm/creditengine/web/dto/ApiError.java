package com.srm.creditengine.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Formato unico de erro para toda a API - critério de aceite do SPEC
 * (usabilidade: "erros de validacao exibidos de forma especifica por
 * campo, nunca como erro generico") e anti-padrao evitado explicitamente
 * (item 12 do desafio: "erro respondido com 200 OK").
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {
    }

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }

    public static ApiError of(int status, String error, String message, String path, List<FieldError> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, path, fieldErrors);
    }
}
