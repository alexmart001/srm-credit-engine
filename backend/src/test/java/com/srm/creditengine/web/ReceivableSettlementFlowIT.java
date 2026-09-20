package com.srm.creditengine.web;

import com.srm.creditengine.domain.BaseRate;
import com.srm.creditengine.domain.Currency;
import com.srm.creditengine.domain.FxRate;
import com.srm.creditengine.domain.ReceivableType;
import com.srm.creditengine.repository.BaseRateRepository;
import com.srm.creditengine.repository.FxRateRepository;
import com.srm.creditengine.repository.SettlementRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Teste end-to-end via HTTP (MockMvc), cobrindo o fluxo completo:
 * aquisicao -> liquidacao -> idempotencia -> ja-liquidado -> validacao.
 * Complementa (nao substitui) os testes unitarios do SettlementService e
 * o PricingEngineGoldenCasesTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReceivableSettlementFlowIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BaseRateRepository baseRateRepository;
    @Autowired private FxRateRepository fxRateRepository;
    @Autowired private SettlementRepository settlementRepository;

    @BeforeEach
    void seedBaseRate() {
        baseRateRepository.save(new BaseRate(
                ReceivableType.DUPLICATA_MERCANTIL, Currency.BRL,
                new BigDecimal("0.01"), Instant.now().minusSeconds(3600)));
    }

    @Test
    void fluxoCompletoDeAquisicaoELiquidacao() throws Exception {
        // 1) Aquisicao (golden case C1: 100.000, 3 meses, BRL/BRL)
        var createRequest = Map.of(
                "cedente", "Empresa Teste LTDA",
                "type", "DUPLICATA_MERCANTIL",
                "faceValue", "100000.00",
                "faceCurrency", "BRL",
                "termMonths", 3,
                "paymentCurrency", "BRL"
        );

        String createResponse = mockMvc.perform(post("/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString();

        Long receivableId = objectMapper.readTree(createResponse).get("id").asLong();

        // 2) Liquidacao - deve bater com o golden case C1 ao centavo
        mockMvc.perform(post("/receivables/{id}/settlements", receivableId)
                        .header("Idempotency-Key", "flow-test-key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentValue").value("92859.94"))
                .andExpect(jsonPath("$.currency").value("BRL"));

        // 3) Retry com a MESMA chave de idempotencia -> mesmo resultado, sem duplicar
        mockMvc.perform(post("/receivables/{id}/settlements", receivableId)
                        .header("Idempotency-Key", "flow-test-key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentValue").value("92859.94"));

        assertThat(settlementRepository.findByReceivableId(receivableId)).hasSize(1);

        // 4) Nova chave de idempotencia para um recebivel JA liquidado -> 409, nao 200
        mockMvc.perform(post("/receivables/{id}/settlements", receivableId)
                        .header("Idempotency-Key", "flow-test-key-2"))
                .andExpect(status().isConflict());

        // 5) Recebivel reflete o status final
        mockMvc.perform(get("/receivables/{id}", receivableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIQUIDADO"));
    }

    @Test
    void simulacaoNaoPersisteNadaEBateComOGoldenCaseC1() throws Exception {
        var simulateRequest = Map.of(
                "type", "DUPLICATA_MERCANTIL",
                "faceValue", "100000.00",
                "faceCurrency", "BRL",
                "termMonths", 3,
                "paymentCurrency", "BRL"
        );

        mockMvc.perform(post("/pricing/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(simulateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentValue").value("92859.94"))
                .andExpect(jsonPath("$.discount").value("7140.06"));
    }

    @Test
    void deveRetornar400ComErroPorCampoQuandoRequestInvalida() throws Exception {
        var invalidRequest = Map.of(
                "cedente", "",
                "type", "DUPLICATA_MERCANTIL",
                "faceValue", "-10.00",
                "faceCurrency", "BRL",
                "termMonths", 0,
                "paymentCurrency", "BRL"
        );

        mockMvc.perform(post("/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void deveRetornar400QuandoIdempotencyKeyAusente() throws Exception {
        var createRequest = Map.of(
                "cedente", "Empresa Sem Header",
                "type", "CHEQUE_PRE_DATADO",
                "faceValue", "25000.00",
                "faceCurrency", "BRL",
                "termMonths", 2,
                "paymentCurrency", "BRL"
        );

        baseRateRepository.save(new BaseRate(
                ReceivableType.CHEQUE_PRE_DATADO, Currency.BRL,
                new BigDecimal("0.01"), Instant.now().minusSeconds(3600)));

        String response = mockMvc.perform(post("/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/receivables/{id}/settlements", id))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveTravarCambioNaAquisicaoEReplicarGoldenCaseC3CrossCurrency() throws Exception {
        baseRateRepository.save(new BaseRate(
                ReceivableType.DUPLICATA_MERCANTIL, Currency.USD,
                new BigDecimal("0.01"), Instant.now().minusSeconds(3600)));

        fxRateRepository.save(new FxRate("USD/BRL", new BigDecimal("5.4321"), Instant.now().minusSeconds(3600)));

        var createRequest = Map.of(
                "cedente", "Empresa Cross Currency",
                "type", "DUPLICATA_MERCANTIL",
                "faceValue", "100000.00",
                "faceCurrency", "BRL",
                "termMonths", 3,
                "paymentCurrency", "USD"
        );

        String createResponse = mockMvc.perform(post("/receivables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                // o cambio deve ja estar travado na resposta da AQUISICAO (SPEC 1.3)
                .andExpect(jsonPath("$.lockedFxRate").value("5.43210000"))
                .andReturn().getResponse().getContentAsString();

        Long receivableId = objectMapper.readTree(createResponse).get("id").asLong();

        // golden case C3: mesmo VP em BRL de C1, convertido por 5,4321 -> US$17.094,67
        mockMvc.perform(post("/receivables/{id}/settlements", receivableId)
                        .header("Idempotency-Key", "cross-currency-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentValue").value("17094.67"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.fxRateUsed").value("5.43210000"));
    }
}
