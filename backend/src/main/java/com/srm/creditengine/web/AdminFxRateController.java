package com.srm.creditengine.web;

import com.srm.creditengine.domain.FxRate;
import com.srm.creditengine.service.FxRateAdminService;
import com.srm.creditengine.web.dto.FxRateResponse;
import com.srm.creditengine.web.dto.PublishFxRateRequest;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Item 4.1.1 do desafio: "armazenar e prover taxas ... com endpoint de
 * atualizacao manual OU integracao mockada". Aqui: as duas opcoes, lado a
 * lado, cada uma explicita sobre o que faz.
 */
@RestController
@RequestMapping("/admin/fx-rates")
public class AdminFxRateController {

    private final FxRateAdminService fxRateAdminService;

    public AdminFxRateController(FxRateAdminService fxRateAdminService) {
        this.fxRateAdminService = fxRateAdminService;
    }

    /** Atualizacao manual - um operador informa a taxa diretamente. */
    @PostMapping
    public FxRateResponse publishManual(@Valid @RequestBody PublishFxRateRequest request) {
        FxRate fxRate = fxRateAdminService.publishManual(request.currencyPair(), request.rate());
        return FxRateResponse.from(fxRate);
    }

    /**
     * Atualizacao via integracao (mockada) - protegida por timeout, retry e
     * circuit breaker (ResilientFxRateGateway). Sempre retorna 200 com a
     * taxa vigente ao final: a nova, se o provedor respondeu; a anterior,
     * se o provedor estava indisponivel (degradacao graciosa, nunca erro
     * 5xx so' porque um provedor terceiro caiu).
     *
     * Rota usa dois segmentos (/{base}/{quote}/refresh) em vez de um unico
     * "USD/BRL", porque um @PathVariable nao pode conter '/' - o proprio
     * Spring MVC interpretaria como dois segmentos de rota distintos. Os
     * dois pedacos sao rejuntados aqui antes de repassar ao service.
     */
    @PostMapping("/{base}/{quote}/refresh")
    public FxRateResponse refresh(@PathVariable String base, @PathVariable String quote) {
        FxRate fxRate = fxRateAdminService.refreshFromExternalProvider(base + "/" + quote);
        return FxRateResponse.from(fxRate);
    }
}
