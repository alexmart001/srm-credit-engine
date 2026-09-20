package com.srm.creditengine.domain;

/**
 * SPEC 1.5: apenas liquidacao total nesta versao - por isso a maquina de
 * estados e' binaria, sem estado intermediario de saldo remanescente.
 */
public enum ReceivableStatus {
    PENDENTE,
    LIQUIDADO
}
