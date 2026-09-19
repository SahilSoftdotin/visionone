package com.visiondigitallab.visionone.auth;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** The authenticated caller, read from the security context rather than passed around. */
@Component
public class CurrentUser {

    /** The Keycloak subject claim; the stable identity key in {@code membership}. */
    public String subject() {
        return jwt().map(Jwt::getSubject)
                .orElseThrow(() -> new IllegalStateException("No authenticated caller"));
    }

    public String displayName() {
        return jwt().map(token -> {
                    String name = token.getClaimAsString("name");
                    return name != null ? name : token.getClaimAsString("preferred_username");
                })
                .orElse("unknown");
    }

    public boolean hasRole(Role role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role.authority()::equals);
    }

    private Optional<Jwt> jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return Optional.of(token.getToken());
        }
        return Optional.empty();
    }
}
