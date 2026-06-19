package com.lionfinance.ironkey.security.jwt;

import com.lionfinance.ironkey.domain.entity.Role;
import com.lionfinance.ironkey.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(
                // "this-is-a-test-secret-key-that-is-at-least-64-characters-long!!" en base64
                "dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LWtleS10aGF0LWlzLWF0LWxlYXN0LTY0LWNoYXJhY3RlcnMtbG9uZyEh",
                900_000L,
                604_800_000L
        );
        jwtService = new JwtService(props);

        testUser = User.builder()
                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .email("test@ironkey.dev")
                .masterPasswordHash("hash")
                .kdfSalt("salt")
                .protectedSymmetricKey("key")
                .protectedSymmetricKeyIv("iv")
                .roles(Set.of(Role.builder().id(UUID.randomUUID()).name("USER").build()))
                .build();
    }

    @Test
    void generateAccessToken_producesValidToken() {
        String token = jwtService.generateAccessToken(testUser);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void extractUserId_returnsCorrectId() {
        String token = jwtService.generateAccessToken(testUser);

        assertThat(jwtService.extractUserId(token))
                .isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void extractEmail_returnsCorrectEmail() {
        String token = jwtService.generateAccessToken(testUser);

        assertThat(jwtService.extractEmail(token)).isEqualTo("test@ironkey.dev");
    }

    @Test
    void isTokenValid_tamperedSignature_returnsFalse() {
        String token = jwtService.generateAccessToken(testUser);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature";

        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void isTokenValid_randomString_returnsFalse() {
        assertThat(jwtService.isTokenValid("not.a.jwt")).isFalse();
    }

    @Test
    void isTokenValid_emptyString_returnsFalse() {
        assertThat(jwtService.isTokenValid("")).isFalse();
    }

    @Test
    void generateRawRefreshToken_producesUniqueTokens() {
        String token1 = jwtService.generateRawRefreshToken();
        String token2 = jwtService.generateRawRefreshToken();

        assertThat(token1).isNotBlank();
        assertThat(token1).isNotEqualTo(token2);
    }
}
