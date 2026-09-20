package com.srm.creditengine.service;

import com.srm.creditengine.domain.*;
import com.srm.creditengine.exception.BaseRateNotFoundException;
import com.srm.creditengine.exception.ConcurrentSettlementException;
import com.srm.creditengine.exception.ReceivableAlreadySettledException;
import com.srm.creditengine.exception.ReceivableNotFoundException;
import com.srm.creditengine.pricing.*;
import com.srm.creditengine.repository.BaseRateRepository;
import com.srm.creditengine.repository.ReceivableRepository;
import com.srm.creditengine.repository.SettlementRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitarios do SettlementService, isolando os repositorios via
 * Mockito. Cobre os quatro ramos de concorrencia/idempotencia descritos
 * no javadoc da classe.
 */
class SettlementServiceTest {

    @Mock private ReceivableRepository receivableRepository;
    @Mock private BaseRateRepository baseRateRepository;
    @Mock private SettlementRepository settlementRepository;

    private PricingEngine pricingEngine; // instancia real - motor ja validado pelos golden cases
    private SettlementService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        pricingEngine = new PricingEngine(new SpreadStrategyFactory(List.of(
                new DuplicataMercantilSpreadStrategy(),
                new ChequePreDatadoSpreadStrategy()
        )));
        service = new SettlementService(receivableRepository, baseRateRepository, settlementRepository, pricingEngine);
    }

    private Receivable pendingReceivable() {
        return new Receivable(
                "Cedente Teste",
                ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100000.00"),
                Currency.BRL,
                3,
                Currency.BRL,
                null,
                Instant.now()
        );
    }

    @Test
    void deveLiquidarComSucessoQuandoRecebivelPendente() {
        Receivable receivable = pendingReceivable();
        BaseRate baseRate = new BaseRate(ReceivableType.DUPLICATA_MERCANTIL, Currency.BRL,
                new BigDecimal("0.01"), Instant.now().minusSeconds(3600));

        when(settlementRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(receivableRepository.findById(1L)).thenReturn(Optional.of(receivable));
        when(baseRateRepository.findEffectiveRate(any(), any(), any())).thenReturn(Optional.of(baseRate));
        when(settlementRepository.saveAndFlush(any(Settlement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(receivableRepository.saveAndFlush(any(Receivable.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Settlement result = service.settle(new SettlementCommand(1L, "key-1"));

        assertThat(result.getPresentValue()).isEqualByComparingTo(new BigDecimal("92859.94"));
        assertThat(receivable.getStatus()).isEqualTo(ReceivableStatus.LIQUIDADO);
        verify(settlementRepository).saveAndFlush(any(Settlement.class));
        verify(receivableRepository).saveAndFlush(receivable);
    }

    @Test
    void deveSerIdempotenteQuandoChaveJaProcessada() {
        Settlement jaExistente = new Settlement(1L, "key-1", new BigDecimal("92859.94"),
                Currency.BRL, new BigDecimal("0.01"), new BigDecimal("0.015"),
                new BigDecimal("0.025"), new BigDecimal("0.025"), null, Instant.now());

        when(settlementRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(jaExistente));

        Settlement result = service.settle(new SettlementCommand(1L, "key-1"));

        assertThat(result).isSameAs(jaExistente);
        // idempotencia: nenhum outro repositorio/servico deve ser tocado
        verifyNoInteractions(receivableRepository);
        verifyNoInteractions(baseRateRepository);
        verify(settlementRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveLancarExcecaoQuandoRecebivelNaoExiste() {
        when(settlementRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(receivableRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.settle(new SettlementCommand(99L, "key-1")))
                .isInstanceOf(ReceivableNotFoundException.class);
    }

    @Test
    void deveRecusarNovaLiquidacaoDeRecebivelJaLiquidado() {
        Receivable jaLiquidado = pendingReceivable();
        jaLiquidado.markAsSettled();

        when(settlementRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());
        when(receivableRepository.findById(1L)).thenReturn(Optional.of(jaLiquidado));

        assertThatThrownBy(() -> service.settle(new SettlementCommand(1L, "key-2")))
                .isInstanceOf(ReceivableAlreadySettledException.class);

        verifyNoInteractions(baseRateRepository);
    }

    @Test
    void deveLancarExcecaoQuandoNaoHaTaxaBaseVigente() {
        Receivable receivable = pendingReceivable();

        when(settlementRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(receivableRepository.findById(1L)).thenReturn(Optional.of(receivable));
        when(baseRateRepository.findEffectiveRate(any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.settle(new SettlementCommand(1L, "key-1")))
                .isInstanceOf(BaseRateNotFoundException.class);

        verify(settlementRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveResolverCorridaDeIdempotenciaDevolvendoResultadoDoVencedor() {
        Receivable receivable = pendingReceivable();
        BaseRate baseRate = new BaseRate(ReceivableType.DUPLICATA_MERCANTIL, Currency.BRL,
                new BigDecimal("0.01"), Instant.now().minusSeconds(3600));
        Settlement doVencedor = new Settlement(1L, "key-1", new BigDecimal("92859.94"),
                Currency.BRL, new BigDecimal("0.01"), new BigDecimal("0.015"),
                new BigDecimal("0.025"), new BigDecimal("0.025"), null, Instant.now());

        when(settlementRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.empty())       // 1a checagem (rapida): ainda nao existe
                .thenReturn(Optional.of(doVencedor)); // apos a corrida: o vencedor ja commitou
        when(receivableRepository.findById(1L)).thenReturn(Optional.of(receivable));
        when(baseRateRepository.findEffectiveRate(any(), any(), any())).thenReturn(Optional.of(baseRate));
        when(settlementRepository.saveAndFlush(any(Settlement.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint violated"));

        Settlement result = service.settle(new SettlementCommand(1L, "key-1"));

        assertThat(result).isSameAs(doVencedor);
        // o recebivel NAO deve ser marcado como liquidado por este chamador (ele perdeu a corrida)
        verify(receivableRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveTraduzirConflitoDeOptimisticLockingParaExcecaoDeDominio() {
        Receivable receivable = pendingReceivable();
        BaseRate baseRate = new BaseRate(ReceivableType.DUPLICATA_MERCANTIL, Currency.BRL,
                new BigDecimal("0.01"), Instant.now().minusSeconds(3600));

        when(settlementRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());
        when(receivableRepository.findById(1L)).thenReturn(Optional.of(receivable));
        when(baseRateRepository.findEffectiveRate(any(), any(), any())).thenReturn(Optional.of(baseRate));
        when(settlementRepository.saveAndFlush(any(Settlement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(receivableRepository.saveAndFlush(any(Receivable.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Receivable.class, 1L));

        assertThatThrownBy(() -> service.settle(new SettlementCommand(1L, "key-2")))
                .isInstanceOf(ConcurrentSettlementException.class);
    }
}
