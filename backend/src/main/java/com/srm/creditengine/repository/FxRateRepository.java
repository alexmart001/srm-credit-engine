package com.srm.creditengine.repository;

import com.srm.creditengine.domain.FxRate;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    /**
     * Usado na AQUISICAO, para travar o cambio (SPEC 1.3). Mesma correcao de
     * seguranca de BaseRateRepository#findEffectiveRate - Limit(1) evita
     * NonUniqueResultException caso mais de uma linha "vigente" exista.
     */
    @Query("""
           SELECT f FROM FxRate f
           WHERE f.currencyPair = :pair
             AND f.validFrom <= :at
             AND (f.validTo IS NULL OR f.validTo > :at)
           ORDER BY f.validFrom DESC
           """)
    Optional<FxRate> findEffectiveRate(@Param("pair") String currencyPair,
                                        @Param("at") Instant at,
                                        Limit limit);

    /** A linha "vigente" atual (sem data de encerramento) para um par - usada ao publicar uma nova taxa. */
    Optional<FxRate> findFirstByCurrencyPairAndValidToIsNullOrderByValidFromDesc(String currencyPair);
}
