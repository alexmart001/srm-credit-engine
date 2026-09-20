package com.srm.creditengine.service;

import com.srm.creditengine.domain.FxRate;
import com.srm.creditengine.domain.Receivable;
import com.srm.creditengine.exception.FxRateNotFoundException;
import com.srm.creditengine.repository.FxRateRepository;
import com.srm.creditengine.repository.ReceivableRepository;
import com.srm.creditengine.web.dto.CreateReceivableRequest;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cadastra (adquire) um recebivel. E' AQUI, na aquisicao - nao na liquidacao -
 * que o cambio e' consultado e travado (SPEC 1.3, rate lock). A liquidacao
 * (SettlementService) apenas reutiliza Receivable.lockedFxRate.
 *
 * DECISAO DE RESILIENCIA (item 6 do desafio): esta classe le a taxa de
 * cambio direto da tabela LOCAL (fx_rates), nunca chama o provedor externo
 * (mockado) diretamente. Isso e' proposital: a aquisicao e' uma transacao
 * financeira sincrona e latencia-sensivel, e nao deveria depender da
 * disponibilidade de um servico externo no caminho critico. Quem fala com o
 * provedor externo - protegido por timeout, retry e circuit breaker - e' o
 * FxRateAdminService, de forma assincrona/administrativa (endpoint manual ou
 * job de atualizacao), mantendo fx_rates sempre com a "ultima taxa boa
 * conhecida". Consequencia direta: se o provedor de cambio cair, NENHUMA
 * aquisicao nem liquidacao em andamento e' afetada - o sistema continua
 * operando com a ultima taxa publicada, e so' a atualizacao dessa taxa fica
 * temporariamente pausada (com o circuit breaker sinalizando isso).
 */
@Service
public class ReceivableService {

    private final ReceivableRepository receivableRepository;
    private final FxRateRepository fxRateRepository;

    public ReceivableService(ReceivableRepository receivableRepository, FxRateRepository fxRateRepository) {
        this.receivableRepository = receivableRepository;
        this.fxRateRepository = fxRateRepository;
    }

    @Transactional
    public Receivable acquire(CreateReceivableRequest request) {
        Instant acquiredAt = Instant.now();
        BigDecimal lockedFxRate = null;

        if (request.paymentCurrency() != request.faceCurrency()) {
            // Convencao de mercado (base/quote): pair = "PAGAMENTO/FACE" e o
            // valor representa "quantas unidades da moeda de FACE por 1
            // unidade da moeda de PAGAMENTO" - e.g. "USD/BRL" = 5.4321
            // significa 1 USD = 5,4321 BRL. Note que o enunciado do desafio
            // rotula a mesma grandeza como "Cambio (BRL/USD)" na tabela de
            // golden cases - rotulo diferente, mesma convencao numerica
            // (padrao de mercado FX), documentado aqui para nao gerar
            // confusao na defesa.
            String pair = request.paymentCurrency() + "/" + request.faceCurrency();
            lockedFxRate = fxRateRepository.findEffectiveRate(pair, acquiredAt, Limit.of(1))
                    .map(FxRate::getRate)
                    .orElseThrow(() -> new FxRateNotFoundException(pair));
        }

        Receivable receivable = new Receivable(
                request.cedente(),
                request.type(),
                request.faceValue(),
                request.faceCurrency(),
                request.termMonths(),
                request.paymentCurrency(),
                lockedFxRate,
                acquiredAt
        );

        return receivableRepository.save(receivable);
    }
}
