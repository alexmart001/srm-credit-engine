package com.srm.creditengine.web;

import com.srm.creditengine.domain.BaseRate;
import com.srm.creditengine.domain.FxRate;
import com.srm.creditengine.exception.BaseRateNotFoundException;
import com.srm.creditengine.exception.FxRateNotFoundException;
import com.srm.creditengine.pricing.PricingEngine;
import com.srm.creditengine.pricing.PricingResult;
import com.srm.creditengine.repository.BaseRateRepository;
import com.srm.creditengine.repository.FxRateRepository;
import com.srm.creditengine.web.dto.SimulatePricingRequest;
import com.srm.creditengine.web.dto.SimulatePricingResponse;

import jakarta.validation.Valid;
import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Simulacao "somente leitura" do motor de precificacao - NAO cria
 * recebivel, NAO trava cambio, NAO persiste nada. E' o que alimenta a
 * simulacao em tempo real do painel do operador (item 4.2.1). A operacao
 * real (que trava cambio e persiste) so' acontece em
 * ReceivableController#acquire.
 */
@RestController
@RequestMapping("/pricing")
public class PricingController {

    private final PricingEngine pricingEngine;
    private final BaseRateRepository baseRateRepository;
    private final FxRateRepository fxRateRepository;

    public PricingController(PricingEngine pricingEngine,
                              BaseRateRepository baseRateRepository,
                              FxRateRepository fxRateRepository) {
        this.pricingEngine = pricingEngine;
        this.baseRateRepository = baseRateRepository;
        this.fxRateRepository = fxRateRepository;
    }

    @PostMapping("/simulate")
    public SimulatePricingResponse simulate(@Valid @RequestBody SimulatePricingRequest request) {
        Instant now = Instant.now();

        BaseRate baseRate = baseRateRepository
                .findEffectiveRate(request.type(), request.paymentCurrency(), now, Limit.of(1))
                .orElseThrow(() -> new BaseRateNotFoundException(request.type(), request.paymentCurrency()));

        BigDecimal fxRate = null;
        if (request.paymentCurrency() != request.faceCurrency()) {
            String pair = request.paymentCurrency() + "/" + request.faceCurrency();
            fxRate = fxRateRepository.findEffectiveRate(pair, now, Limit.of(1))
                    .map(FxRate::getRate)
                    .orElseThrow(() -> new FxRateNotFoundException(pair));
        }

        PricingResult result = pricingEngine.price(
                request.faceValue(), request.type(), request.termMonths(), baseRate.getRate(), fxRate);

        return SimulatePricingResponse.from(request.faceValue(), request.paymentCurrency().name(), result);
    }
}
