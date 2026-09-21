package com.srm.creditengine.service;

import com.srm.creditengine.domain.BaseRate;
import com.srm.creditengine.domain.Currency;
import com.srm.creditengine.domain.ReceivableType;
import com.srm.creditengine.repository.BaseRateRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Endpoint de atualizacao manual de taxa base (item 4.1.1 do desafio - o
 * mesmo requisito que motivou os endpoints de cambio em AdminFxRateController,
 * agora fechando a paridade para a outra taxa que o motor de precificacao
 * usa). Nao ha' integracao externa mockada para taxa base (ao contrario do
 * cambio) porque o enunciado nao define uma fonte de mercado para ela -
 * so' atualizacao manual mesmo (ver SPEC 1.2).
 */
@Service
public class BaseRateAdminService {

    private final BaseRateRepository baseRateRepository;

    public BaseRateAdminService(BaseRateRepository baseRateRepository) {
        this.baseRateRepository = baseRateRepository;
    }

    @Transactional
    public BaseRate publish(ReceivableType type, Currency currency, BigDecimal rate) {
        Instant now = Instant.now();

        baseRateRepository.findFirstByReceivableTypeAndCurrencyAndValidToIsNullOrderByValidFromDesc(type, currency)
                .ifPresent(previous -> previous.closeValidityAt(now));

        return baseRateRepository.save(new BaseRate(type, currency, rate, now));
    }
}
