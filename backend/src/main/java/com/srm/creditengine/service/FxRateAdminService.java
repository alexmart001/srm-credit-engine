package com.srm.creditengine.service;

import com.srm.creditengine.domain.FxRate;
import com.srm.creditengine.exception.FxProviderUnavailableException;
import com.srm.creditengine.fx.ResilientFxRateGateway;
import com.srm.creditengine.repository.FxRateRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Administra a tabela local fx_rates - a fonte que ReceivableService
 * consulta na aquisicao (sempre local, nunca externa - ver javadoc de
 * ReceivableService). Duas formas de publicar uma taxa, refletindo o item
 * 4.1.1 do desafio ("endpoint de atualizacao manual OU integracao mockada"):
 *
 *  - publishManual: um operador informa a taxa diretamente (endpoint manual).
 *  - refreshFromExternalProvider: busca no provedor externo mockado, atras
 *    do ResilientFxRateGateway (timeout + retry + circuit breaker). Se o
 *    provedor falhar mesmo apos as tentativas de resiliencia, a taxa local
 *    atual e' MANTIDA sem alteracao (degradacao graciosa) - o metodo nao
 *    lanca excecao para esse caso, apenas informa que nao houve atualizacao.
 *
 * Em ambos os casos, a linha "vigente" anterior (validTo IS NULL) e'
 * fechada antes de inserir a nova, para nunca deixar duas vigentes ao
 * mesmo tempo (correcao aplicada junto de FxRateRepository#findEffectiveRate).
 */
@Service
public class FxRateAdminService {

    private static final Logger log = LoggerFactory.getLogger(FxRateAdminService.class);

    private final FxRateRepository fxRateRepository;
    private final ResilientFxRateGateway resilientFxRateGateway;

    public FxRateAdminService(FxRateRepository fxRateRepository, ResilientFxRateGateway resilientFxRateGateway) {
        this.fxRateRepository = fxRateRepository;
        this.resilientFxRateGateway = resilientFxRateGateway;
    }

    @Transactional
    public FxRate publishManual(String currencyPair, BigDecimal rate) {
        return publish(currencyPair, rate);
    }

    /**
     * @return a taxa vigente apos a tentativa - a nova, se o provedor
     *         respondeu; a anterior (inalterada), se o provedor falhou.
     */
    @Transactional
    public FxRate refreshFromExternalProvider(String currencyPair) {
        try {
            BigDecimal freshRate = resilientFxRateGateway.fetchRate(currencyPair);
            log.atInfo()
                    .addKeyValue("event", "fx_rate_refreshed")
                    .addKeyValue("currencyPair", currencyPair)
                    .addKeyValue("rate", freshRate)
                    .log("Taxa de cambio atualizada a partir do provedor externo");
            return publish(currencyPair, freshRate);
        } catch (Exception providerFailure) {
            // Cobre FxProviderUnavailableException (esgotou retry/circuit
            // breaker) e CallNotPermittedException (circuit aberto) do
            // Resilience4j - em qualquer caso, degradamos sem propagar erro:
            // a aquisicao continua funcionando com a ultima taxa boa conhecida.
            Optional<FxRate> current = fxRateRepository
                    .findFirstByCurrencyPairAndValidToIsNullOrderByValidFromDesc(currencyPair);

            log.atWarn()
                    .addKeyValue("event", "fx_rate_refresh_degraded")
                    .addKeyValue("currencyPair", currencyPair)
                    .addKeyValue("reason", providerFailure.getClass().getSimpleName())
                    .addKeyValue("keptRate", current.map(FxRate::getRate).map(BigDecimal::toPlainString).orElse(null))
                    .log("Provedor de cambio indisponivel - mantendo ultima taxa conhecida");

            return current.orElseThrow(() -> new FxProviderUnavailableException(
                    "Provedor de cambio indisponivel e nenhuma taxa local conhecida para " + currencyPair,
                    providerFailure));
        }
    }

    private FxRate publish(String currencyPair, BigDecimal rate) {
        Instant now = Instant.now();

        fxRateRepository.findFirstByCurrencyPairAndValidToIsNullOrderByValidFromDesc(currencyPair)
                .ifPresent(previous -> previous.closeValidityAt(now));

        return fxRateRepository.save(new FxRate(currencyPair, rate, now));
    }
}
