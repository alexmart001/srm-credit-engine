package com.srm.creditengine.service;

/**
 * idempotencyKey e' fornecida pelo CHAMADOR (tipicamente um header
 * Idempotency-Key na requisicao HTTP) - o servico nunca gera essa chave,
 * apenas a usa como chave de deduplicacao.
 */
public record SettlementCommand(Long receivableId, String idempotencyKey) {

    public SettlementCommand {
        if (receivableId == null) {
            throw new IllegalArgumentException("receivableId e' obrigatorio");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey e' obrigatoria");
        }
    }
}
