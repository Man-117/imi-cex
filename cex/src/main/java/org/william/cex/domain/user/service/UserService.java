package org.william.cex.domain.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.william.cex.api.exception.InsufficientBalanceException;
import org.william.cex.api.exception.UserNotFoundException;
import org.william.cex.domain.user.entity.User;
import org.william.cex.domain.user.entity.UserAccount;
import org.william.cex.domain.user.entity.UserWallet;
import org.william.cex.domain.user.repository.UserAccountRepository;
import org.william.cex.domain.user.repository.UserRepository;
import org.william.cex.domain.user.repository.UserWalletRepository;
import org.william.cex.infrastructure.cache.CacheManager;

import java.math.BigDecimal;

@Service
@Slf4j
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserWalletRepository walletRepository;

    @Autowired
    private UserAccountRepository accountRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public User registerUser(String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(User.UserRole.USER)
                .kycStatus(User.KycStatus.PENDING)
                .build();

        user = userRepository.save(user);

        // Create user account
        UserAccount account = UserAccount.builder()
                .userId(user.getId())
                .totalDeposits(BigDecimal.ZERO)
                .totalWithdrawals(BigDecimal.ZERO)
                .build();
        accountRepository.save(account);

        log.info("User registered: {}", email);
        return user;
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));
    }

    /**
     * Authenticate user with email and password
     * @param email user email
     * @param password raw password
     * @return authenticated user
     * @throws UserNotFoundException if user not found
     * @throws IllegalArgumentException if password is invalid
     */
    public User authenticateUser(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("Invalid password attempt for user: {}", email);
            throw new IllegalArgumentException("Invalid password");
        }

        log.info("User authenticated successfully: {}", email);
        return user;
    }

    @Transactional
    public void addBalance(Long userId, String currency, BigDecimal amount) {
        User user = getUserById(userId);

        UserWallet wallet = walletRepository.findByUserIdAndCurrency(userId, currency)
                .orElse(UserWallet.builder()
                        .userId(userId)
                        .currency(currency)
                        .balance(BigDecimal.ZERO)
                        .lockedAmount(BigDecimal.ZERO)
                        .build());

        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        // Update user account
        UserAccount account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException("User account not found"));
        account.setTotalDeposits(account.getTotalDeposits().add(amount));
        accountRepository.save(account);

        // Invalidate cache
        cacheManager.clearBalance(userId, currency);

        log.info("Balance added for user {} currency {}: {}", userId, currency, amount);
    }

    public UserWallet getWallet(Long userId, String currency) {
        // Try cache first
        Object cached = cacheManager.getBalance(userId, currency);
        if (cached instanceof UserWallet) {
            return (UserWallet) cached;
        }

        UserWallet wallet = walletRepository.findByUserIdAndCurrency(userId, currency)
                .orElseThrow(() -> new UserNotFoundException("Wallet not found for currency: " + currency));

        // Cache for 5 minutes
        cacheManager.setBalance(userId, currency, wallet, 5);
        return wallet;
    }

    @Transactional
    public void lockBalance(Long userId, String currency, BigDecimal amount) {
        UserWallet wallet = getWallet(userId, currency);

        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance. Available: " + wallet.getAvailableBalance());
        }

        wallet.lock(amount);
        walletRepository.save(wallet);
        cacheManager.clearBalance(userId, currency);

        log.info("Balance locked for user {} currency {}: {}", userId, currency, amount);
    }

    @Transactional
    public void unlockBalance(Long userId, String currency, BigDecimal amount) {
        UserWallet wallet = getWallet(userId, currency);
        wallet.unlock(amount);
        walletRepository.save(wallet);
        cacheManager.clearBalance(userId, currency);

        log.info("Balance unlocked for user {} currency {}: {}", userId, currency, amount);
    }
}

