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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final ReceivableRepository receivableRepository;
    private final BaseRateRepository baseRateRepository;
    private final SettlementRepository settlementRepository;
    private final PricingEngine pricingEngine;

    public SettlementService(ReceivableRepository receivableRepository,
                              BaseRateRepository baseRateRepository,
                              SettlementRepository settlementRepository,
                              PricingEngine pricingEngine) {
        this.receivableRepository = receivableRepository;
        this.baseRateRepository = baseRateRepository;
        this.settlementRepository = settlementRepository;
        this.pricingEngine = pricingEngine;
    }

    @Transactional
    public Settlement settle(SettlementCommand command) {

        // --- Caminho rapido de idempotencia (retry legitimo) ---
        var existing = settlementRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Liquidacao idempotente: idempotencyKey={} ja processada, devolvendo resultado existente",
                    command.idempotencyKey());
            return existing.get();
        }

        Receivable receivable = receivableRepository.findById(command.receivableId())
                .orElseThrow(() -> new ReceivableNotFoundException(command.receivableId()));

        if (receivable.getStatus() == ReceivableStatus.LIQUIDADO) {
            // Chave de idempotencia NOVA para um recebivel JA liquidado -
            // isso nao e' um retry, e' uma segunda liquidacao indevida.
            throw new ReceivableAlreadySettledException(receivable.getId());
        }

        java.math.BigDecimal baseRate = resolveBaseRate(receivable);
        var fxRateLocked = receivable.isCrossCurrency() ? receivable.getLockedFxRate() : null;

        PricingResult result = pricingEngine.price(
                receivable.getFaceValue(),
                receivable.getType(),
                receivable.getTermMonths(),
                baseRate,
                fxRateLocked
        );

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
            log.warn("Corrida de idempotencia detectada para idempotencyKey={} - outra requisicao venceu",
                    command.idempotencyKey());
            return settlementRepository.findByIdempotencyKey(command.idempotencyKey())
                    .orElseThrow(() -> race);
        }

        receivable.markAsSettled();
        try {
            receivableRepository.saveAndFlush(receivable);
        } catch (ObjectOptimisticLockingFailureException conflict) {
            log.warn("Conflito de optimistic locking ao liquidar receivableId={} - " +
                    "outra liquidacao concorrente alterou o registro primeiro", receivable.getId());
            throw new ConcurrentSettlementException(receivable.getId(), conflict);
        }

        return settlement;
    }

    private java.math.BigDecimal resolveBaseRate(Receivable receivable) {
        ReceivableType type = receivable.getType();
        Currency currency = receivable.getPaymentCurrency();
        BaseRate baseRate = baseRateRepository.findEffectiveRate(type, currency, Instant.now())
                .orElseThrow(() -> new BaseRateNotFoundException(type, currency));
        return baseRate.getRate();
    }
}
