package com.lionfinance.ironkey.security.jwt;

import com.lionfinance.ironkey.domain.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String ISSUER   = "ironkey-api";
    private static final String AUDIENCE = "ironkey-client";

    private final JwtProperties jwtProperties;

    public String generateAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                // Permite invalidar tokens ya emitidos (p. ej. tras recovery) sin esperar
                // a que expiren — ver AuthService.recoverAccount.
                .claim("tv", user.getTokenVersion())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.accessTokenExpiration()))
                .signWith(signingKey())
                .compact();
    }

    // El refresh token es un valor opaco aleatorio, no un JWT.
    // Se devuelve crudo al cliente y se guarda hasheado en DB para revocación real.
    public String generateRawRefreshToken() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    public int extractTokenVersion(String token) {
        Integer tv = parseClaims(token).get("tv", Integer.class);
        return tv != null ? tv : 0;
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
    }
}
