package com.srm.creditengine.repository;

import com.srm.creditengine.domain.BaseRate;
import com.srm.creditengine.domain.Currency;
import com.srm.creditengine.domain.ReceivableType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface BaseRateRepository extends JpaRepository<BaseRate, Long> {

    /**
     * Busca a taxa base vigente para (tipo, moeda) em um instante dado -
     * mesma logica de "ultima vigencia <= timestamp" usada para FxRate,
     * conforme SPEC 1.2.
     *
     * BUG ENCONTRADO E CORRIGIDO durante a implementacao da feature de
     * resiliencia de cambio: o retorno Optional<BaseRate> faz o Spring Data
     * chamar getSingleResult() internamente, que lanca
     * NonUniqueResultException se mais de uma linha "vigente" (validTo IS
     * NULL) existir ao mesmo tempo para o mesmo (tipo, moeda) - cenario que
     * passou a ser possivel assim que endpoints de escrita para taxas
     * passaram a existir. O parametro Limit (Spring Data 3.2+) forca
     * "no maximo 1 resultado" na propria query, independente de quantas
     * linhas "vigentes" existam no dado (defesa em profundidade - o ideal
     * e' NUNCA ter mais de uma linha vigente ao mesmo tempo, mas a query
     * fica segura mesmo se esse invariante for violado por engano).
     */
    @Query("""
           SELECT b FROM BaseRate b
           WHERE b.receivableType = :type
             AND b.currency = :currency
             AND b.validFrom <= :at
             AND (b.validTo IS NULL OR b.validTo > :at)
           ORDER BY b.validFrom DESC
           """)
    Optional<BaseRate> findEffectiveRate(@Param("type") ReceivableType type,
                                          @Param("currency") Currency currency,
                                          @Param("at") Instant at,
                                          Limit limit);

    /** A linha "vigente" atual (sem data de encerramento) - usada ao publicar uma nova taxa. */
    Optional<BaseRate> findFirstByReceivableTypeAndCurrencyAndValidToIsNullOrderByValidFromDesc(
            ReceivableType type, Currency currency);
}
