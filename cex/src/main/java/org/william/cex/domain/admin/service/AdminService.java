package org.william.cex.domain.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.william.cex.api.exception.UnauthorizedException;
import org.william.cex.api.exception.UserNotFoundException;
import org.william.cex.domain.admin.entity.Administrator;
import org.william.cex.domain.admin.repository.AdministratorRepository;
import org.william.cex.domain.user.entity.User;
import org.william.cex.domain.user.repository.UserRepository;

import java.util.Optional;

@Service
@Slf4j
public class AdminService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdministratorRepository administratorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${admin.registration.key:admin-secret-key}")
    private String adminRegistrationKey;

    /**
     * Register a new admin account
     * @param email Admin email
     * @param password Admin password
     * @param adminKey Admin registration key
     * @return Created admin user
     * @throws IllegalArgumentException if email is already registered or admin key is invalid
     */
    @Transactional
    public User registerAdmin(String email, String password, String adminKey) {
        // Validate admin key
        if (!adminRegistrationKey.equals(adminKey)) {
            log.warn("Invalid admin registration key provided");
            throw new IllegalArgumentException("Invalid admin registration key");
        }

        // Check if email already exists
        if (userRepository.existsByEmail(email)) {
            log.warn("Attempted to register admin with existing email: {}", email);
            throw new IllegalArgumentException("Email already registered");
        }

        // Create admin user with ADMIN role
        User adminUser = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(User.UserRole.ADMIN)
                .kycStatus(User.KycStatus.APPROVED)
                .build();

        adminUser = userRepository.save(adminUser);

        // Create administrator record
        Administrator administrator = Administrator.builder()
                .userId(adminUser.getId())
                .permissions(null) // Default permissions, can be customized
                .build();

        administratorRepository.save(administrator);

        log.info("Admin user registered successfully: {}", email);
        return adminUser;
    }

    /**
     * Login admin account
     * @param email Admin email
     * @param password Admin password
     * @return Admin user if authentication successful
     * @throws UserNotFoundException if user not found
     * @throws UnauthorizedException if password is incorrect or user is not admin
     */
    @Transactional(readOnly = true)
    public User loginAdmin(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Login attempt with non-existent email: {}", email);
                    return new UserNotFoundException("Admin not found: " + email);
                });

        // Verify user is an admin
        if (!user.getRole().equals(User.UserRole.ADMIN)) {
            log.warn("Non-admin user {} attempted to login via admin endpoint", email);
            throw new UnauthorizedException("User is not an admin");
        }

        // Verify password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("Invalid password for admin login attempt: {}", email);
            throw new UnauthorizedException("Invalid password");
        }

        log.info("Admin login successful: {}", email);
        return user;
    }

    /**
     * Get admin by user ID
     * @param userId User ID
     * @return Administrator record if exists
     */
    @Transactional(readOnly = true)
    public Optional<Administrator> getAdminByUserId(Long userId) {
        return administratorRepository.findByUserId(userId);
    }

    /**
     * Check if a user is an admin
     * @param userId User ID
     * @return true if user is admin, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isAdmin(Long userId) {
        return administratorRepository.findByUserId(userId).isPresent();
    }
}

