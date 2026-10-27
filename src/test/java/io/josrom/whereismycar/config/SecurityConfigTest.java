package io.josrom.whereismycar.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTest {
    private static final String PROJECT_ID = "my-project";
    private static final String ISSUER = "https://securetoken.google.com/" + PROJECT_ID;

    private final OAuth2TokenValidator<Jwt> validator = SecurityConfig.firebaseTokenValidator(PROJECT_ID);

    @Test
    void acceptsAFirebaseTokenOfTheProject() {
        assertThat(validator.validate(token(ISSUER, PROJECT_ID, Instant.now().plusSeconds(600))).hasErrors()).isFalse();
    }

    @Test
    void rejectsATokenForAnotherAudience() {
        assertThat(validator.validate(token(ISSUER, "other-project", Instant.now().plusSeconds(600))).hasErrors()).isTrue();
    }

    @Test
    void rejectsATokenFromAnotherIssuer() {
        assertThat(validator.validate(token("https://evil.example.com", PROJECT_ID, Instant.now().plusSeconds(600))).hasErrors()).isTrue();
    }

    @Test
    void rejectsAnExpiredToken() {
        assertThat(validator.validate(token(ISSUER, PROJECT_ID, Instant.now().minusSeconds(600))).hasErrors()).isTrue();
    }

    private static Jwt token(String issuer, String audience, Instant expiresAt) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(issuer)
                .audience(List.of(audience))
                .subject("uid")
                .issuedAt(expiresAt.minusSeconds(3600))
                .expiresAt(expiresAt)
                .build();
    }
}
