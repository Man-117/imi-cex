package org.william.cex.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Utility class for extracting and validating authentication credentials
 */
@Component
@Slf4j
public class AuthenticationUtils {

    /**
     * Extract authenticated user's email from SecurityContext
     */
    public String getAuthenticatedUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            log.debug("Request secured with authenticated user: {}", authentication.getPrincipal());
            return (String) authentication.getPrincipal();
        }
        log.warn("Request attempted without proper authentication");
        throw new IllegalArgumentException("User not authenticated");
    }

    /**
     * Check if authenticated user has a specific role
     */
    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String requiredRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .anyMatch(auth -> auth.equals(requiredRole));
    }

    /**
     * Get the role of the authenticated user
     */
    public Optional<String> getAuthenticatedUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        return authentication.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .filter(auth -> auth.startsWith("ROLE_"))
                .findFirst();
    }

    /**
     * Validate if the user is authenticated
     */
    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}

