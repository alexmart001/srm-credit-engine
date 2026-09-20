package com.srm.creditengine.repository;

import com.srm.creditengine.domain.Currency;
import com.srm.creditengine.domain.Settlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    /** Suporte a idempotencia: se a chave ja existe, a liquidacao nao deve ser refeita. */
    Optional<Settlement> findByIdempotencyKey(String idempotencyKey);

    List<Settlement> findByReceivableId(Long receivableId);

    /**
     * Extrato de liquidacao (item 4.1.6 do desafio): filtro por periodo,
     * cedente e moeda, paginado. Settlement.receivableId nao e' um
     * relacionamento JPA mapeado (a entidade e' propositalmente imutavel e
     * "burra" - so' guarda o id), entao o join com Receivable e' feito via
     * ON explicito (suportado desde JPA 2.1) em vez de um path de associacao.
     *
     * Nivel pleno+ do desafio sugere query builder / SQL nativo em vez de
     * ORM puro para relatorios - JPQL foi a escolha inicial deste skeleton
     * por simplicidade; se o profiling mostrar necessidade, trocar por
     * @Query(nativeQuery = true) e' a proxima otimizacao natural aqui.
     */
    @Query("""
           SELECT s FROM Settlement s
           JOIN Receivable r ON r.id = s.receivableId
           WHERE (:cedente IS NULL OR r.cedente = :cedente)
             AND (:currency IS NULL OR s.settlementCurrency = :currency)
             AND (:from IS NULL OR s.settledAt >= :from)
             AND (:to IS NULL OR s.settledAt <= :to)
           """)
    Page<Settlement> findExtrato(@Param("cedente") String cedente,
                                  @Param("currency") Currency currency,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to,
                                  Pageable pageable);
}
