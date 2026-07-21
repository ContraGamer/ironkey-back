package com.lionfinance.ironkey.domain.repository;

import com.lionfinance.ironkey.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Sesiones activas del usuario (para mostrar en panel de sesiones)
    List<RefreshToken> findAllByUserIdAndRevokedFalseAndExpiresAtAfter(
            UUID userId, OffsetDateTime now
    );

    // Revocar una sesión específica por id verificando que es del usuario
    Optional<RefreshToken> findByIdAndUserId(UUID id, UUID userId);

    // Revocar todas las sesiones del usuario (logout de todos los dispositivos)
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = :now WHERE r.user.id = :userId AND r.revoked = false")
    void revokeAllByUserId(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    // Eliminar tokens expirados o revocados (para un job de limpieza)
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now OR r.revoked = true")
    int deleteExpiredAndRevoked(@Param("now") OffsetDateTime now);
}
