package com.visiondigitallab.visionone.support;

import com.visiondigitallab.visionone.auth.Role;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;

/** Builds the JWTs the tenant-isolation matrix needs, without a running Keycloak. */
public final class TestTokens {

    public static final String VISION_ADMIN_SUBJECT = "11111111-1111-4111-8111-111111111111";
    public static final String CLIENT_OWNER_SUBJECT = "22222222-2222-4222-8222-222222222222";
    public static final String OUTSIDER_SUBJECT = "33333333-3333-4333-8333-333333333333";

    private TestTokens() {}

    public static Jwt tokenFor(String subject, Role... roles) {
        List<String> roleNames = Arrays.stream(roles).map(Enum::name).toList();
        return base(subject).claim("realm_access", Map.of("roles", roleNames)).build();
    }

    /** A token carrying no roles at all, for the "role stripped" row of the matrix. */
    public static Jwt tokenWithoutRoles(String subject) {
        return base(subject).claim("realm_access", Map.of("roles", List.of())).build();
    }

    private static Jwt.Builder base(String subject) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .claim("preferred_username", subject);
    }
}
