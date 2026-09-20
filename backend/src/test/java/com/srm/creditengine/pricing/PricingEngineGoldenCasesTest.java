package com.srm.creditengine.pricing;

import com.srm.creditengine.domain.ReceivableType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Afericiao obrigatoria (item 4.3 do desafio): o motor precisa reproduzir
 * estes tres valores AO CENTAVO. As premissas de taxa base (1% a.m.),
 * arredondamento (half-even, 2 casas, so no resultado final) e prazo em
 * meses inteiros sao as premissas FIXAS dos golden cases - independentes
 * das escolhas gerais do SPEC.md (que valem para o resto do sistema).
 */
class PricingEngineGoldenCasesTest {

    private static final BigDecimal GOLDEN_BASE_RATE = new BigDecimal("0.01"); // 1,00% a.m.

    private final PricingEngine engine = new PricingEngine(
            new SpreadStrategyFactory(List.of(
                    new DuplicataMercantilSpreadStrategy(),
                    new ChequePreDatadoSpreadStrategy()
            ))
    );

    @Test
    @DisplayName("C1 - Duplicata Mercantil, R00.000, 3 meses, BRL -> VP = R2.859,94")
    void goldenCase1_duplicataMercantilBRL() {
        PricingResult result = engine.price(
                new BigDecimal("100000.00"),
                ReceivableType.DUPLICATA_MERCANTIL,
                3,
                GOLDEN_BASE_RATE,
                null // sem cross-currency
        );

        assertThat(result.presentValueSettlementCurrency())
                .isEqualByComparingTo(new BigDecimal("92859.94"));
    }

    @Test
    @DisplayName("C2 - Cheque Pre-datado, R5.000, 2 meses, BRL -> VP = R3.337,77")
    void goldenCase2_chequePreDatadoBRL() {
        PricingResult result = engine.price(
                new BigDecimal("25000.00"),
                ReceivableType.CHEQUE_PRE_DATADO,
                2,
                GOLDEN_BASE_RATE,
                null
        );

        assertThat(result.presentValueSettlementCurrency())
                .isEqualByComparingTo(new BigDecimal("23337.77"));
    }

    @Test
    @DisplayName("C3 - Duplicata Mercantil, R00.000, 3 meses, USD @5,4321 -> VP = US7.094,67")
    void goldenCase3_duplicataMercantilCrossCurrencyUSD() {
        PricingResult result = engine.price(
                new BigDecimal("100000.00"),
                ReceivableType.DUPLICATA_MERCANTIL,
                3,
                GOLDEN_BASE_RATE,
                new BigDecimal("5.4321") // cambio travado na aquisicao (SPEC 1.3)
        );

        // VP em BRL (ja arredondado) deve bater com C1 - o desagio em BRL e' o mesmo.
        assertThat(result.presentValueFaceCurrency())
                .isEqualByComparingTo(new BigDecimal("92859.94"));

        assertThat(result.presentValueSettlementCurrency())
                .isEqualByComparingTo(new BigDecimal("17094.67"));
    }

    @Test
    @DisplayName("SPEC 1.6 - taxa efetiva negativa e' aplicada como zero, mas o valor raw e' preservado")
    void effectiveRateFloorAtZero() {
        // taxa base fortemente negativa hipotetica, so para validar o piso
        PricingResult result = engine.price(
                new BigDecimal("10000.00"),
                ReceivableType.DUPLICATA_MERCANTIL,
                1,
                new BigDecimal("-0.05"), // -5% a.m. (cenario hipotetico de teste)
                null
        );

        assertThat(result.effectiveRateRaw()).isEqualByComparingTo(new BigDecimal("-0.035"));
        assertThat(result.effectiveRateApplied()).isEqualByComparingTo(BigDecimal.ZERO);
        // com taxa aplicada = 0, VP = VF (sem deságio)
        assertThat(result.presentValueSettlementCurrency())
                .isEqualByComparingTo(new BigDecimal("10000.00"));
    }
}
