package com.srm.creditengine.service;

import com.srm.creditengine.domain.*;
import com.srm.creditengine.exception.ConcurrentSettlementException;
import com.srm.creditengine.exception.ReceivableAlreadySettledException;
import com.srm.creditengine.repository.BaseRateRepository;
import com.srm.creditengine.repository.ReceivableRepository;
import com.srm.creditengine.repository.SettlementRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Requisito de nivel senior (secao 6 do desafio): "um teste que demonstra
 * o conflito sendo tratado (duas liquidacoes simultaneas do mesmo recebivel)".
 *
 * Duas threads chamam SettlementService#settle() para o MESMO recebivel,
 * com idempotency keys DIFERENTES (ou seja: nao e' o caso de retry legitimo -
 * e' uma tentativa real de liquidar duas vezes), disparadas o mais proximo
 * possivel uma da outra via CountDownLatch.
 *
 * Nao afirmamos QUAL excecao a thread perdedora recebe - dependendo do
 * timing exato da corrida, ela pode ver o Receivable ja LIQUIDADO
 * (ReceivableAlreadySettledException) OU perder o optimistic lock no
 * commit (ConcurrentSettlementException). As DUAS sao respostas corretas e
 * seguras. O que a garantia de negocio exige - e o que este teste afirma
 * com certeza - e' que EXATAMENTE UMA liquidacao e' criada, nunca duas.
 */
@SpringBootTest
class SettlementServiceConcurrencyIT {

    @Autowired private SettlementService settlementService;
    @Autowired private ReceivableRepository receivableRepository;
    @Autowired private BaseRateRepository baseRateRepository;
    @Autowired private SettlementRepository settlementRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void apenasUmaDasDuasLiquidacoesSimultaneasDeveSerAplicada() throws InterruptedException {
        Long receivableId = transactionTemplate.execute(status -> {
            baseRateRepository.save(new BaseRate(
                    ReceivableType.DUPLICATA_MERCANTIL, Currency.BRL,
                    new BigDecimal("0.01"), Instant.now().minusSeconds(3600)));

            Receivable receivable = new Receivable(
                    "Cedente Concorrencia",
                    ReceivableType.DUPLICATA_MERCANTIL,
                    new BigDecimal("100000.00"),
                    Currency.BRL,
                    3,
                    Currency.BRL,
                    null,
                    Instant.now()
            );
            return receivableRepository.save(receivable).getId();
        });

        int numThreads = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        AtomicInteger sucessos = new AtomicInteger(0);
        AtomicInteger falhasEsperadas = new AtomicInteger(0);
        AtomicInteger falhasInesperadas = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            String idempotencyKey = "concurrency-attempt-" + i;
            pool.submit(() -> {
                try {
                    startLatch.await(); // as duas threads liberam o mais proximo possivel uma da outra
                    settlementService.settle(new SettlementCommand(receivableId, idempotencyKey));
                    sucessos.incrementAndGet();
                } catch (ConcurrentSettlementException | ReceivableAlreadySettledException expected) {
                    falhasEsperadas.incrementAndGet();
                } catch (Exception unexpected) {
                    falhasInesperadas.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).as("threads devem terminar dentro do timeout").isTrue();
        assertThat(falhasInesperadas.get()).as("nenhuma excecao inesperada deve ocorrer").isZero();
        assertThat(sucessos.get()).as("exatamente uma liquidacao deve ter sucesso").isEqualTo(1);
        assertThat(falhasEsperadas.get()).as("a outra tentativa deve falhar de forma segura").isEqualTo(1);

        List<Settlement> settlements = settlementRepository.findByReceivableId(receivableId);
        assertThat(settlements).as("exatamente um registro de liquidacao deve existir no banco").hasSize(1);

        Receivable finalState = receivableRepository.findById(receivableId).orElseThrow();
        assertThat(finalState.getStatus()).isEqualTo(ReceivableStatus.LIQUIDADO);
    }
}
