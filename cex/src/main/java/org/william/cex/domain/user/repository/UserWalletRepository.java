package org.william.cex.domain.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.william.cex.domain.user.entity.UserWallet;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserWalletRepository extends JpaRepository<UserWallet, Long> {
    Optional<UserWallet> findByUserIdAndCurrency(Long userId, String currency);
    List<UserWallet> findByUserId(Long userId);
}

