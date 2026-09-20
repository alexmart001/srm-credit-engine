package com.srm.creditengine.fx;

import com.srm.creditengine.exception.FxProviderUnavailableException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.*;

/**
 * Protege a chamada ao provedor externo (mockado) de cambio com tres
 * camadas de resiliencia, na ordem em que atuam de fora para dentro:
 *
 *  1) CIRCUIT BREAKER ("fxProvider", config em application.yml) - se a taxa
 *     de falhas recentes passar do limiar, para de tentar chamar o provedor
 *     por um tempo (evita insistir batendo num servico que sabemos estar
 *     fora do ar, e falha rapido).
 *  2) RETRY ("fxProvider") - dentro de uma unica "permissao" do circuit
 *     breaker, tenta novamente algumas vezes com backoff antes de desistir
 *     (cobre falhas transitorias curtas).
 *  3) TIMEOUT - implementado manualmente (ExecutorService + Future#get com
 *     prazo), nao via TimeLimiter do Resilience4j. Motivo documentado: o
 *     TimeLimiter do Resilience4j exige que o metodo anotado retorne
 *     CompletableFuture (API assincrona), o que obrigaria toda a cadeia de
 *     chamada (incluindo ReceivableService/FxRateAdminService) a virar
 *     assincrona so' por causa desta unica dependencia externa - trade-off
 *     que nao valia a pena no escopo deste skeleton. Um Future com timeout
 *     explicito da' a mesma garantia (nao esperar indefinidamente por uma
 *     chamada travada) com uma API sincrona simples.
 *
 * Se as tres camadas se esgotarem, a excecao propaga para o chamador
 * (FxRateAdminService), que decide a degradacao (manter a ultima taxa
 * conhecida) - este gateway NAO decide fallback de negocio, so' encapsula
 * a chamada externa e suas garantias de resiliencia.
 */
@Component
public class ResilientFxRateGateway {

    private static final Logger log = LoggerFactory.getLogger(ResilientFxRateGateway.class);
    private static final long TIMEOUT_MILLIS = 2000;

    private final ExternalFxRateProvider externalProvider;
    private final ExecutorService timeoutExecutor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "fx-provider-call");
                t.setDaemon(true);
                return t;
            });

    public ResilientFxRateGateway(ExternalFxRateProvider externalProvider) {
        this.externalProvider = externalProvider;
    }

    @CircuitBreaker(name = "fxProvider")
    @Retry(name = "fxProvider")
    public BigDecimal fetchRate(String currencyPair) {
        Future<BigDecimal> future = timeoutExecutor.submit(() -> externalProvider.fetchRate(currencyPair));
        try {
            return future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.atWarn()
                    .addKeyValue("event", "fx_provider_timeout")
                    .addKeyValue("currencyPair", currencyPair)
                    .addKeyValue("timeoutMillis", TIMEOUT_MILLIS)
                    .log("Timeout ao consultar provedor de cambio");
            throw new FxProviderUnavailableException(
                    "Timeout (" + TIMEOUT_MILLIS + "ms) ao consultar provedor de cambio para " + currencyPair, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof FxProviderUnavailableException fxEx) {
                throw fxEx;
            }
            throw new FxProviderUnavailableException(
                    "Falha inesperada ao consultar provedor de cambio para " + currencyPair, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FxProviderUnavailableException("Consulta ao provedor de cambio interrompida", e);
        }
    }
}
