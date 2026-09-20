package com.srm.creditengine.web;

import com.srm.creditengine.domain.Currency;
import com.srm.creditengine.domain.Settlement;
import com.srm.creditengine.exception.SettlementNotFoundException;
import com.srm.creditengine.repository.SettlementRepository;
import com.srm.creditengine.web.dto.SettlementResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Rota analitica separada da rota de comando (SettlementController) -
 * separacao deliberada entre escrita (liquidar) e leitura (consultar
 * extrato), util como ponto de partida caso a proposta de EDA (nivel
 * Staff/TL) evolua para CQRS de fato.
 */
@RestController
@RequestMapping("/settlements")
public class SettlementQueryController {

    private final SettlementRepository settlementRepository;

    public SettlementQueryController(SettlementRepository settlementRepository) {
        this.settlementRepository = settlementRepository;
    }

    @GetMapping("/{id}")
    public SettlementResponse get(@PathVariable Long id) {
        Settlement settlement = settlementRepository.findById(id)
                .orElseThrow(() -> new SettlementNotFoundException(id));
        return SettlementResponse.from(settlement);
    }

    /** Extrato de liquidacao (item 4.1.6): GET /settlements?cedente=&currency=&from=&to=&page=&size= */
    @GetMapping
    public Page<SettlementResponse> extrato(
            @RequestParam(required = false) String cedente,
            @RequestParam(required = false) Currency currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "settledAt") Pageable pageable) {

        return settlementRepository.findExtrato(cedente, currency, from, to, pageable)
                .map(SettlementResponse::from);
    }
}
