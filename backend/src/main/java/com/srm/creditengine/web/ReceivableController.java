package com.srm.creditengine.web;

import com.srm.creditengine.domain.Receivable;
import com.srm.creditengine.exception.ReceivableNotFoundException;
import com.srm.creditengine.repository.ReceivableRepository;
import com.srm.creditengine.service.ReceivableService;
import com.srm.creditengine.web.dto.CreateReceivableRequest;
import com.srm.creditengine.web.dto.ReceivableResponse;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/receivables")
public class ReceivableController {

    private final ReceivableService receivableService;
    private final ReceivableRepository receivableRepository;

    public ReceivableController(ReceivableService receivableService, ReceivableRepository receivableRepository) {
        this.receivableService = receivableService;
        this.receivableRepository = receivableRepository;
    }

    /**
     * Cadastra (adquire) um recebivel. Se paymentCurrency != faceCurrency,
     * o cambio vigente NESTE momento e' consultado e TRAVADO (SPEC 1.3) -
     * a liquidacao posterior reutiliza esse valor, nao consulta cambio de novo.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReceivableResponse acquire(@Valid @RequestBody CreateReceivableRequest request) {
        Receivable receivable = receivableService.acquire(request);
        return ReceivableResponse.from(receivable);
    }

    @GetMapping("/{id}")
    public ReceivableResponse get(@PathVariable Long id) {
        Receivable receivable = receivableRepository.findById(id)
                .orElseThrow(() -> new ReceivableNotFoundException(id));
        return ReceivableResponse.from(receivable);
    }
}
