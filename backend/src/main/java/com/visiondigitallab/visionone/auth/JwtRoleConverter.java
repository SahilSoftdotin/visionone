package com.visiondigitallab.visionone.auth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Maps Keycloak's {@code realm_access.roles} onto Spring authorities.
 *
 * <p>This is the only place that knows the shape of the identity provider's token. Swapping
 * Keycloak for another OIDC provider is a change to this converter and some configuration.
 */
public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getClaimAsString("preferred_username"));
    }

    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        Collection<GrantedAuthority> granted = new ArrayList<>();
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS);
        if (realmAccess == null || !(realmAccess.get(ROLES) instanceof List<?> roles)) {
            return granted;
        }
        for (Object role : roles) {
            String name = String.valueOf(role);
            for (Role known : Role.values()) {
                if (known.name().equals(name)) {
                    granted.add(new SimpleGrantedAuthority(known.authority()));
                }
            }
        }
        return granted;
    }
}
