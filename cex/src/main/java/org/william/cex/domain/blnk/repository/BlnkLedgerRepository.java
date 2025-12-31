package org.william.cex.domain.blnk.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.william.cex.domain.blnk.entity.BlnkLedger;

import java.util.Optional;

@Repository
public interface BlnkLedgerRepository extends JpaRepository<BlnkLedger, Long> {
    Optional<BlnkLedger> findByUserId(Long userId);
    Optional<BlnkLedger> findByBlnkAccountId(String blnkAccountId);
}

