package com.srm.creditengine.domain;

import java.math.BigDecimal;

/**
 * Tipos de recebivel suportados. Cada tipo carrega seu proprio spread
 * (regra de negocio fixa do enunciado), mas o desenho de Strategy
 * (ver pacote pricing) evita que o enum vire um "switch" espalhado
 * pelo codigo - o enum so guarda o dado, a estrategia decide o que fazer com ele.
 */
public enum ReceivableType {

    DUPLICATA_MERCANTIL(new BigDecimal("0.015")),   // 1,5% a.m.
    CHEQUE_PRE_DATADO(new BigDecimal("0.025"));     // 2,5% a.m.

    private final BigDecimal monthlySpread;

    ReceivableType(BigDecimal monthlySpread) {
        this.monthlySpread = monthlySpread;
    }

    public BigDecimal getMonthlySpread() {
        return monthlySpread;
    }
}
