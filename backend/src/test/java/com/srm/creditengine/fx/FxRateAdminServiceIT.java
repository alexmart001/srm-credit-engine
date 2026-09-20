package com.srm.creditengine.fx;

import com.srm.creditengine.domain.FxRate;
import com.srm.creditengine.repository.FxRateRepository;
import com.srm.creditengine.service.FxRateAdminService;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstra a resiliencia da integracao (mockada) de cambio - item 6 do
 * desafio: "o que acontece se o provedor de taxa cai no meio de uma
 * liquidacao?". Resposta demonstrada aqui: nada acontece com liquidacao
 * nenhuma (ela nem consulta o provedor - ver javadoc de ReceivableService);
 * o que acontece e' que a ATUALIZACAO da taxa local fica pausada, e o
 * sistema continua servindo a ultima taxa boa conhecida sem erro.
 *
 * @Transactional na classe de teste: cada metodo roda na sua propria
 * transacao, desfeita ao final - evita que os dois testes interfiram no
 * estado um do outro, mesmo dividindo o mesmo contexto Spring/banco H2.
 * Pares de moeda distintos por metodo sao uma segunda camada de isolamento,
 * mais facil de ler do que depender so' do rollback.
 */
@SpringBootTest
@Transactional
class FxRateAdminServiceIT {

    private static final String PAIR_HEALTHY_TEST = "USD/BRL";
    private static final String PAIR_OUTAGE_TEST = "EUR/BRL";

    @Autowired private FxRateAdminService fxRateAdminService;
    @Autowired private MockExternalFxRateProvider mockProvider;
    @Autowired private FxRateRepository fxRateRepository;
    @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * O CircuitBreakerRegistry e' um bean singleton compartilhado entre
     * metodos de teste (o contexto Spring e' reaproveitado). Sem resetar
     * aqui, um teste que abre o circuito (outage) deixaria o circuito
     * OPEN para o proximo teste que espera o provedor saudavel - um
     * vazamento de estado entre testes classico o suficiente para valer
     * o comentario.
     */
    @AfterEach
    void resetState() {
        mockProvider.simulateOutage(false);
        mockProvider.simulateLatency(0);
        circuitBreakerRegistry.circuitBreaker("fxProvider").reset();
    }

    @Test
    void refreshComProvedorSaudavelAtualizaATaxaEFechaAVigenciaAnterior() {
        String pair = PAIR_HEALTHY_TEST;

        FxRate primeira = fxRateAdminService.publishManual(pair, new BigDecimal("5.0000"));
        assertThat(primeira.getValidTo()).isNull();

        mockProvider.setMockRate(pair, new BigDecimal("5.5000"));
        FxRate atualizada = fxRateAdminService.refreshFromExternalProvider(pair);

        assertThat(atualizada.getRate()).isEqualByComparingTo("5.5000");
        assertThat(atualizada.getValidTo()).isNull();

        // a linha anterior deve ter sido fechada, nao deixada "vigente" junto com a nova
        List<FxRate> todas = fxRateRepository.findAll();
        long vigentes = todas.stream()
                .filter(f -> f.getCurrencyPair().equals(pair) && f.getValidTo() == null)
                .count();
        assertThat(vigentes).as("apenas uma linha vigente por par, nunca duas").isEqualTo(1);
    }

    @Test
    void refreshComProvedorIndisponivelDegradaGraciosamenteSemLancarExcecao() {
        String pair = PAIR_OUTAGE_TEST;

        FxRate original = fxRateAdminService.publishManual(pair, new BigDecimal("5.0000"));

        mockProvider.simulateOutage(true);

        // chamadas suficientes para o circuit breaker acumular amostras
        // (minimum-number-of-calls: 5, ver application.yml) - cada chamada
        // aqui ja' passou por 3 tentativas internas de retry antes de falhar.
        for (int i = 0; i < 5; i++) {
            FxRate resultado = fxRateAdminService.refreshFromExternalProvider(pair);
            // nunca lanca excecao para o chamador - sempre devolve uma taxa utilizavel
            assertThat(resultado.getRate()).isEqualByComparingTo(original.getRate());
        }

        // nenhuma linha nova foi inserida - a aquisicao continua usando a taxa original
        List<FxRate> todasDoPar = fxRateRepository.findAll().stream()
                .filter(f -> f.getCurrencyPair().equals(pair))
                .toList();
        assertThat(todasDoPar).hasSize(1);
        assertThat(todasDoPar.get(0).getRate()).isEqualByComparingTo("5.0000");
        assertThat(todasDoPar.get(0).getValidTo()).isNull();
    }
}
