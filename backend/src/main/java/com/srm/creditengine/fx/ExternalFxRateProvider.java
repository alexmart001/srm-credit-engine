package com.srm.creditengine.fx;

import java.math.BigDecimal;

/**
 * Abstracao da integracao (mockada, conforme item 6 do desafio: "timeout +
 * retry ou circuit breaker na integracao (mockada) de cambio") com um
 * provedor externo de cotacoes. Em producao seria um cliente HTTP para uma
 * API de mercado (ex.: um banco de dados de cambio, uma API tipo
 * OpenExchangeRates); aqui e' simulada por MockExternalFxRateProvider.
 */
public interface ExternalFxRateProvider {

    /**
     * @param currencyPair ex.: "USD/BRL"
     * @return a cotacao atual segundo o provedor
     * @throws FxProviderUnavailableException se o provedor estiver indisponivel
     */
    BigDecimal fetchRate(String currencyPair);
}
