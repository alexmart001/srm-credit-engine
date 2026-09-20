package com.srm.creditengine.service;

import com.srm.creditengine.domain.BaseRate;
import com.srm.creditengine.domain.Currency;
import com.srm.creditengine.domain.Receivable;
import com.srm.creditengine.domain.ReceivableStatus;
import com.srm.creditengine.domain.ReceivableType;
import com.srm.creditengine.domain.Settlement;
import com.srm.creditengine.exception.BaseRateNotFoundException;
import com.srm.creditengine.exception.ConcurrentSettlementException;
import com.srm.creditengine.exception.ReceivableAlreadySettledException;
import com.srm.creditengine.exception.ReceivableNotFoundException;
import com.srm.creditengine.pricing.PricingEngine;
import com.srm.creditengine.pricing.PricingResult;
import com.srm.creditengine.repository.BaseRateRepository;
import com.srm.creditengine.repository.ReceivableRepository;
import com.srm.creditengine.repository.SettlementRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Orquestra a liquidacao de um recebivel. Duas garantias de concorrencia
 * distintas, exigidas pelo desafio, sao tratadas aqui:
 *
 * 1) IDEMPOTENCIA (item 4.1.3 do desafio): a mesma requisicao repetida
 *    (retry de rede, duplo clique) nao pode gerar duas liquidacoes. A fonte
 *    de verdade e' a constraint UNIQUE em settlements.idempotency_key
 *    (V4__create_settlements.sql) - a checagem "de leitura" abaixo
 *    (findByIdempotencyKey) e' so' um atalho para o caso comum (sem corrida);
 *    a corrida genuina (duas requisicoes com a MESMA chave, simultaneas) e'
 *    resolvida pela constraint do banco: quem perde a corrida do INSERT cai
 *    no catch de DataIntegrityViolationException e devolve o resultado do
 *    vencedor, em vez de duplicar.
 *
 * 2) OPTIMISTIC LOCKING (nivel senior, item 6): duas liquidacoes concorrentes
 *    do MESMO recebivel (chaves de idempotencia DIFERENTES - ou seja, nao e'
 *    o caso de retry, e' de fato uma tentativa dupla indevida) sao impedidas
 *    pelo campo @Version em Receivable. Quem perde a corrida do UPDATE recebe
 *    ObjectOptimisticLockingFailureException, traduzido aqui para
 *    ConcurrentSettlementException.
 *
 * Toda a operacao roda em uma unica transacao: se a atualizacao do Receivable
 * falhar (passo 2), o Settlement ja gravado no passo 1 e' revertido junto -
 * nao existe "liquidacao pela metade" (item 4.1.3).
 *
 * OBSERVABILIDADE (nivel senior, item 6): cada chamada emite (a) logs
 * estruturados via a API fluente do SLF4J (log.atInfo().addKeyValue(...)) -
 * o Spring Boot 3.4+ converte esses pares chave-valor em campos JSON
 * discretos quando 'logging.structured.format.console' esta habilitado
 * (ver docker-compose.yml) - e (b) metricas de negocio via Micrometer:
 *   - srm.settlements.total{outcome=...}    contador por desfecho
 *   - srm.settlement.duration{outcome=...}  latencia por desfecho
 *   - srm.pricing.duration                  latencia isolada do motor de calculo
 * Todas expostas em /actuator/prometheus.
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final ReceivableRepository receivableRepository;
    private final BaseRateRepository baseRateRepository;
    private final SettlementRepository settlementRepository;
    private final PricingEngine pricingEngine;
    private final MeterRegistry meterRegistry;

    public SettlementService(ReceivableRepository receivableRepository,
                              BaseRateRepository baseRateRepository,
                              SettlementRepository settlementRepository,
                              PricingEngine pricingEngine,
                              MeterRegistry meterRegistry) {
        this.receivableRepository = receivableRepository;
        this.baseRateRepository = baseRateRepository;
        this.settlementRepository = settlementRepository;
        this.pricingEngine = pricingEngine;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public Settlement settle(SettlementCommand command) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "error"; // sobrescrito em todo caminho conhecido; "error" so' sobrevive a um caso nao mapeado

        try {
            // --- Caminho rapido de idempotencia (retry legitimo) ---
            var existing = settlementRepository.findByIdempotencyKey(command.idempotencyKey());
            if (existing.isPresent()) {
                outcome = "idempotent_replay";
                log.atInfo()
                        .addKeyValue("event", "settlement_idempotent_replay")
                        .addKeyValue("receivableId", command.receivableId())
                        .addKeyValue("idempotencyKey", command.idempotencyKey())
                        .log("Liquidacao idempotente - devolvendo resultado existente");
                return existing.get();
            }

            Receivable receivable = receivableRepository.findById(command.receivableId())
                    .orElseThrow(() -> new ReceivableNotFoundException(command.receivableId()));

            if (receivable.getStatus() == ReceivableStatus.LIQUIDADO) {
                // Chave de idempotencia NOVA para um recebivel JA liquidado -
                // isso nao e' um retry, e' uma segunda liquidacao indevida.
                throw new ReceivableAlreadySettledException(receivable.getId());
            }

            BigDecimal baseRate = resolveBaseRate(receivable);
            var fxRateLocked = receivable.isCrossCurrency() ? receivable.getLockedFxRate() : null;

            PricingResult result = meterRegistry.timer("srm.pricing.duration")
                    .record(() -> pricingEngine.price(
                            receivable.getFaceValue(),
                            receivable.getType(),
                            receivable.getTermMonths(),
                            baseRate,
                            fxRateLocked
                    ));

            Instant settledAt = Instant.now();

            Settlement settlement = new Settlement(
                    receivable.getId(),
                    command.idempotencyKey(),
                    result.presentValueSettlementCurrency(),
                    receivable.getPaymentCurrency(),
                    result.baseRateUsed(),
                    result.spreadUsed(),
                    result.effectiveRateRaw(),
                    result.effectiveRateApplied(),
                    result.fxRateApplied(),
                    settledAt
            );

            try {
                // saveAndFlush forca a checagem da constraint UNIQUE agora, dentro
                // da transacao, para que a corrida (duas requisicoes com a mesma
                // idempotency key) seja detectada aqui, nao silenciosamente no commit.
                settlement = settlementRepository.saveAndFlush(settlement);
            } catch (DataIntegrityViolationException race) {
                outcome = "idempotency_race_resolved";
                log.atWarn()
                        .addKeyValue("event", "settlement_idempotency_race")
                        .addKeyValue("idempotencyKey", command.idempotencyKey())
                        .log("Corrida de idempotencia detectada - outra requisicao venceu");
                return settlementRepository.findByIdempotencyKey(command.idempotencyKey())
                        .orElseThrow(() -> race);
            }

            receivable.markAsSettled();
            try {
                receivableRepository.saveAndFlush(receivable);
            } catch (ObjectOptimisticLockingFailureException conflict) {
                throw new ConcurrentSettlementException(receivable.getId(), conflict);
            }

            outcome = "success";
            log.atInfo()
                    .addKeyValue("event", "settlement_completed")
                    .addKeyValue("receivableId", receivable.getId())
                    .addKeyValue("settlementId", settlement.getId())
                    .addKeyValue("presentValue", settlement.getPresentValue())
                    .addKeyValue("currency", settlement.getSettlementCurrency())
                    .log("Liquidacao concluida");
            return settlement;

        } catch (ReceivableNotFoundException e) {
            outcome = "receivable_not_found";
            throw e;
        } catch (ReceivableAlreadySettledException e) {
            outcome = "already_settled";
            log.atWarn()
                    .addKeyValue("event", "settlement_rejected_already_settled")
                    .addKeyValue("receivableId", command.receivableId())
                    .log("Tentativa de liquidar recebivel ja liquidado");
            throw e;
        } catch (BaseRateNotFoundException e) {
            outcome = "base_rate_not_found";
            throw e;
        } catch (ConcurrentSettlementException e) {
            outcome = "concurrency_conflict";
            log.atWarn()
                    .addKeyValue("event", "settlement_concurrency_conflict")
                    .addKeyValue("receivableId", command.receivableId())
                    .log("Conflito de optimistic locking - outra liquidacao concorrente venceu");
            throw e;
        } finally {
            sample.stop(meterRegistry.timer("srm.settlement.duration", "outcome", outcome));
            meterRegistry.counter("srm.settlements.total", "outcome", outcome).increment();
        }
    }

    private BigDecimal resolveBaseRate(Receivable receivable) {
        ReceivableType type = receivable.getType();
        Currency currency = receivable.getPaymentCurrency();
        BaseRate baseRate = baseRateRepository.findEffectiveRate(type, currency, Instant.now())
                .orElseThrow(() -> new BaseRateNotFoundException(type, currency));
        return baseRate.getRate();
    }
}
