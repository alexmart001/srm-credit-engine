package com.srm.creditengine.web;

import com.srm.creditengine.domain.Settlement;
import com.srm.creditengine.service.SettlementCommand;
import com.srm.creditengine.service.SettlementService;
import com.srm.creditengine.web.dto.SettlementResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de liquidacao (item 4.1.3 do desafio - o mesmo que o Anexo A
 * implementa de forma insegura). Exige o header Idempotency-Key: a MESMA
 * chave repetida (retry de rede, duplo clique) devolve o MESMO resultado,
 * sem gerar uma segunda liquidacao (ver SettlementService).
 *
 * Retorna sempre 200 OK (nunca 201) mesmo na primeira liquidacao bem
 * sucedida - decisao deliberada: por ser uma operacao idempotente por
 * chave, o corpo da resposta representa "o estado desta liquidacao",
 * nao necessariamente "um recurso recem-criado" do ponto de vista do
 * cliente que fez o retry. Alternativa (201 na 1a vez, 200 no replay)
 * exigiria a service expor se houve replay - avaliado como complexidade
 * desnecessaria para o ganho semantico neste caso.
 */
@RestController
@RequestMapping("/receivables/{receivableId}/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping
    public ResponseEntity<SettlementResponse> settle(
            @PathVariable Long receivableId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        Settlement settlement = settlementService.settle(new SettlementCommand(receivableId, idempotencyKey));
        return ResponseEntity.status(HttpStatus.OK).body(SettlementResponse.from(settlement));
    }
}
