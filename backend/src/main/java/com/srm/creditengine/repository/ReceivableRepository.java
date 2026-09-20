package com.srm.creditengine.repository;

import com.srm.creditengine.domain.Receivable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceivableRepository extends JpaRepository<Receivable, Long> {
}
