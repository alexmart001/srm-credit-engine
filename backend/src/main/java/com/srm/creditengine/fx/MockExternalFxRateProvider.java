package com.srm.creditengine.fx;

import com.srm.creditengine.exception.FxProviderUnavailableException;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Integracao MOCKADA com um provedor externo de cambio (item 6 do desafio
 * pede explicitamente uma integracao mockada, nao uma real). Simula dois
 * comportamentos controlaveis, uteis tanto para a demonstracao na defesa
 * ao vivo quanto para os testes automatizados:
 *
 *  - simulateOutage(true): toda chamada falha com FxProviderUnavailableException
 *    (simula o provedor fora do ar).
 *  - simulateLatency(millis): toda chamada bloqueia por N ms antes de
 *    responder (simula lentidao de rede - util para observar o timeout
 *    agindo antes do circuit breaker).
 *
 * Em produção, esta classe seria substituida por um cliente HTTP real
 * (ex.: WebClient/RestClient) para uma API de cambio de mercado - a
 * interface ExternalFxRateProvider e' o ponto de extensao para isso.
 */
@Component
public class MockExternalFxRateProvider implements ExternalFxRateProvider {

    private final Map<String, BigDecimal> mockRates = new ConcurrentHashMap<>(Map.of(
            "USD/BRL", new BigDecimal("5.4321")
    ));

    private final AtomicBoolean simulateOutage = new AtomicBoolean(false);
    private volatile long simulatedLatencyMillis = 0;

    @Override
    public BigDecimal fetchRate(String currencyPair) {
        if (simulateOutage.get()) {
            throw new FxProviderUnavailableException(
                    "Provedor de cambio (mock) indisponivel para " + currencyPair);
        }

        if (simulatedLatencyMillis > 0) {
            try {
                Thread.sleep(simulatedLatencyMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FxProviderUnavailableException("Requisicao ao provedor de cambio interrompida", e);
            }
        }

        BigDecimal rate = mockRates.get(currencyPair);
        if (rate == null) {
            throw new FxProviderUnavailableException("Par de moedas nao suportado pelo mock: " + currencyPair);
        }
        return rate;
    }

    // --- Controles usados por testes e pela demonstracao na defesa ao vivo ---

    public void setMockRate(String currencyPair, BigDecimal rate) {
        mockRates.put(currencyPair, rate);
    }

    public void simulateOutage(boolean outage) {
        simulateOutage.set(outage);
    }

    public void simulateLatency(long millis) {
        this.simulatedLatencyMillis = millis;
    }
}
