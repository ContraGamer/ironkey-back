package com.lionfinance.ironkey.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    // Hash de la salida KDF del cliente — nunca el master_password en claro
    @Column(name = "master_password_hash", nullable = false, length = 255)
    private String masterPasswordHash;

    // Parámetros KDF que el cliente necesita para derivar su clave en login
    @Column(name = "kdf_type", nullable = false, length = 20)
    @Builder.Default
    private String kdfType = "argon2id";

    @Column(name = "kdf_iterations", nullable = false)
    @Builder.Default
    private Integer kdfIterations = 3;

    @Column(name = "kdf_memory", nullable = false)
    @Builder.Default
    private Integer kdfMemory = 65536;

    @Column(name = "kdf_parallelism", nullable = false)
    @Builder.Default
    private Integer kdfParallelism = 4;

    @Column(name = "kdf_salt", nullable = false, length = 255)
    private String kdfSalt;

    // Clave simétrica del vault cifrada con la master_derived_key del cliente
    @Column(name = "protected_symmetric_key", nullable = false, columnDefinition = "TEXT")
    private String protectedSymmetricKey;

    @Column(name = "protected_symmetric_key_iv", nullable = false, length = 255)
    private String protectedSymmetricKeyIv;

    // 2FA / TOTP
    @Column(name = "totp_secret", length = 255)
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    @Builder.Default
    private Boolean totpEnabled = false;

    // Recuperación de cuenta (activa solo si feature flag del servidor está en true)
    @Column(name = "recovery_enabled", nullable = false)
    @Builder.Default
    private Boolean recoveryEnabled = false;

    @Column(name = "recovery_code_hash", columnDefinition = "TEXT")
    private String recoveryCodeHash;

    @Column(name = "recovery_protected_key", columnDefinition = "TEXT")
    private String recoveryProtectedKey;

    @Column(name = "recovery_protected_key_iv", length = 255)
    private String recoveryProtectedKeyIv;

    // Preferencias del usuario
    @Column(name = "require_reprompt", nullable = false)
    @Builder.Default
    private Boolean requireReprompt = false;

    @Column(name = "vault_timeout_minutes", nullable = false)
    @Builder.Default
    private Integer vaultTimeoutMinutes = 15;

    // Account lockout
    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    // Se incrementa en eventos que deben invalidar los access tokens ya emitidos
    // (p. ej. recovery). El JWT lleva este valor como claim "tv"; si no coincide, se rechaza.
    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private Integer tokenVersion = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
}
