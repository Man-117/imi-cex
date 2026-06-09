package org.william.cex.domain.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.william.cex.exception.InsufficientBalanceException;
import org.william.cex.exception.UserNotFoundException;
import org.william.cex.domain.user.entity.User;
import org.william.cex.domain.user.entity.UserAccount;
import org.william.cex.domain.user.entity.UserWallet;
import org.william.cex.domain.user.repository.UserAccountRepository;
import org.william.cex.domain.user.repository.UserRepository;
import org.william.cex.domain.user.repository.UserWalletRepository;
import org.william.cex.infrastructure.cache.CacheManager;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

        String normalizedCurrency = normalizeCurrency(currency);
        UserWallet wallet = getOrCreateWallet(userId, normalizedCurrency);

        wallet.setBalance(scale(wallet.getBalance().add(scale(amount))));
        walletRepository.save(wallet);

        // Update user account
        UserAccount account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException("User account not found"));
        account.setTotalDeposits(account.getTotalDeposits().add(amount));
        accountRepository.save(account);

        // Invalidate cache
        cacheManager.clearBalance(userId, normalizedCurrency);

        log.info("Balance added for user {} currency {}: {}", userId, normalizedCurrency, amount);
    }

    public UserWallet getWallet(Long userId, String currency) {
        String normalizedCurrency = normalizeCurrency(currency);
        // Try cache first
        Object cached = cacheManager.getBalance(userId, normalizedCurrency);
        if (cached instanceof UserWallet) {
            return (UserWallet) cached;
        }

        UserWallet wallet = walletRepository.findByUserIdAndCurrency(userId, normalizedCurrency)
                .orElseThrow(() -> new UserNotFoundException("Wallet not found for currency: " + normalizedCurrency));

        // Cache for 5 minutes
        cacheManager.setBalance(userId, normalizedCurrency, wallet, 5);
        return wallet;
    }

    @Transactional
    public void lockBalance(Long userId, String currency, BigDecimal amount) {
        String normalizedCurrency = normalizeCurrency(currency);
        UserWallet wallet = getWallet(userId, normalizedCurrency);
        BigDecimal normalizedAmount = scale(amount);

        if (wallet.getAvailableBalance().compareTo(normalizedAmount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance. Available: " + wallet.getAvailableBalance());
        }

        wallet.lock(normalizedAmount);
        walletRepository.save(wallet);
        cacheManager.clearBalance(userId, normalizedCurrency);

        log.info("Balance locked for user {} currency {}: {}", userId, normalizedCurrency, normalizedAmount);
    }

    @Transactional
    public void unlockBalance(Long userId, String currency, BigDecimal amount) {
        String normalizedCurrency = normalizeCurrency(currency);
        BigDecimal normalizedAmount = scale(amount);
        UserWallet wallet = getWallet(userId, normalizedCurrency);
        wallet.unlock(normalizedAmount);
        walletRepository.save(wallet);
        cacheManager.clearBalance(userId, normalizedCurrency);

        log.info("Balance unlocked for user {} currency {}: {}", userId, normalizedCurrency, normalizedAmount);
    }

    @Transactional
    public void settleBuyTrade(Long userId,
                               String baseCurrency,
                               String quoteCurrency,
                               BigDecimal tradeAmount,
                               BigDecimal executionPrice,
                               BigDecimal limitPrice,
                               BigDecimal feeAmount) {
        String normalizedBaseCurrency = normalizeCurrency(baseCurrency);
        String normalizedQuoteCurrency = normalizeCurrency(quoteCurrency);
        BigDecimal normalizedTradeAmount = scale(tradeAmount);
        BigDecimal normalizedExecutionPrice = scale(executionPrice);
        BigDecimal normalizedLimitPrice = scale(limitPrice);
        BigDecimal normalizedFeeAmount = scale(feeAmount);

        UserWallet quoteWallet = getWallet(userId, normalizedQuoteCurrency);
        BigDecimal reservedQuote = scale(normalizedTradeAmount.multiply(normalizedLimitPrice));
        BigDecimal quoteCost = scale(normalizedTradeAmount.multiply(normalizedExecutionPrice));
        BigDecimal totalQuoteDebit = scale(quoteCost.add(normalizedFeeAmount));

        if (quoteWallet.getLockedAmount().compareTo(reservedQuote) < 0) {
            throw new InsufficientBalanceException("Insufficient locked quote balance for settlement");
        }
        if (quoteWallet.getBalance().compareTo(totalQuoteDebit) < 0) {
            throw new InsufficientBalanceException("Insufficient quote balance for settlement");
        }

        quoteWallet.unlock(reservedQuote);
        quoteWallet.setBalance(scale(quoteWallet.getBalance().subtract(totalQuoteDebit)));
        walletRepository.save(quoteWallet);

        UserWallet baseWallet = getOrCreateWallet(userId, normalizedBaseCurrency);
        baseWallet.setBalance(scale(baseWallet.getBalance().add(normalizedTradeAmount)));
        walletRepository.save(baseWallet);

        cacheManager.clearBalance(userId, normalizedBaseCurrency);
        cacheManager.clearBalance(userId, normalizedQuoteCurrency);
    }

    @Transactional
    public void settleSellTrade(Long userId,
                                String baseCurrency,
                                String quoteCurrency,
                                BigDecimal tradeAmount,
                                BigDecimal executionPrice,
                                BigDecimal feeAmount) {
        String normalizedBaseCurrency = normalizeCurrency(baseCurrency);
        String normalizedQuoteCurrency = normalizeCurrency(quoteCurrency);
        BigDecimal normalizedTradeAmount = scale(tradeAmount);
        BigDecimal normalizedExecutionPrice = scale(executionPrice);
        BigDecimal normalizedFeeAmount = scale(feeAmount);

        UserWallet baseWallet = getWallet(userId, normalizedBaseCurrency);
        BigDecimal baseDebit = normalizedTradeAmount;

        if (baseWallet.getLockedAmount().compareTo(baseDebit) < 0) {
            throw new InsufficientBalanceException("Insufficient locked base balance for settlement");
        }

        baseWallet.unlock(baseDebit);
        baseWallet.setBalance(scale(baseWallet.getBalance().subtract(baseDebit)));
        walletRepository.save(baseWallet);

        UserWallet quoteWallet = getOrCreateWallet(userId, normalizedQuoteCurrency);
        BigDecimal quoteProceeds = scale(normalizedTradeAmount.multiply(normalizedExecutionPrice).subtract(normalizedFeeAmount));
        quoteWallet.setBalance(scale(quoteWallet.getBalance().add(quoteProceeds)));
        walletRepository.save(quoteWallet);

        cacheManager.clearBalance(userId, normalizedBaseCurrency);
        cacheManager.clearBalance(userId, normalizedQuoteCurrency);
    }

    private UserWallet getOrCreateWallet(Long userId, String currency) {
        return walletRepository.findByUserIdAndCurrency(userId, currency)
                .orElse(UserWallet.builder()
                        .userId(userId)
                        .currency(currency)
                        .balance(BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP))
                        .lockedAmount(BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP))
                        .build());
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String currency) {
        return currency.toUpperCase();
    }
}

