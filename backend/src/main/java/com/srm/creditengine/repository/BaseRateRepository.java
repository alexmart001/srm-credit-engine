package com.srm.creditengine.repository;

import com.srm.creditengine.domain.BaseRate;
import com.srm.creditengine.domain.Currency;
import com.srm.creditengine.domain.ReceivableType;
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
                                          @Param("at") Instant at);
}
