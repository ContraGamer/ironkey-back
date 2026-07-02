package com.lionfinance.ironkey.service;

import com.lionfinance.ironkey.api.dto.auth.request.*;
import com.lionfinance.ironkey.api.dto.auth.response.*;
import com.lionfinance.ironkey.domain.entity.RefreshToken;
import com.lionfinance.ironkey.domain.entity.User;
import com.lionfinance.ironkey.domain.repository.RefreshTokenRepository;
import com.lionfinance.ironkey.domain.repository.RoleRepository;
import com.lionfinance.ironkey.domain.repository.UserRepository;
import com.lionfinance.ironkey.exception.*;
import com.lionfinance.ironkey.security.EncryptionService;
import com.lionfinance.ironkey.security.LockoutProperties;
import com.lionfinance.ironkey.security.jwt.JwtProperties;
import com.lionfinance.ironkey.security.jwt.JwtService;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final LockoutProperties lockoutProperties;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionService encryptionService;
    private final SecretGenerator totpSecretGenerator;
    private final QrGenerator qrGenerator;
    private final CodeVerifier codeVerifier;

    @Value("${ironkey.recovery.enabled}")
    private boolean recoveryEnabled;

    // Hash BCrypt fijo y válido (de un valor arbitrario) usado solo para gastar el mismo
    // tiempo de cómputo cuando el email no existe. Nunca puede coincidir con una password real.
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    // -------------------------------------------------------------------------
    // Registro
    // -------------------------------------------------------------------------

    public AuthResponse register(RegisterRequest request, String ip, String userAgent) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException();
        }

        var userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("Rol USER no encontrado — verifique las migraciones"));

        var user = User.builder()
                .email(request.email())
                .masterPasswordHash(passwordEncoder.encode(request.masterPasswordHash()))
                .kdfSalt(request.kdfSalt())
                .kdfType(request.kdfType())
                .kdfIterations(request.kdfIterations())
                .kdfMemory(request.kdfMemory())
                .kdfParallelism(request.kdfParallelism())
                .protectedSymmetricKey(request.protectedSymmetricKey())
                .protectedSymmetricKeyIv(request.protectedSymmetricKeyIv())
                .roles(Set.of(userRole))
                .build();

        userRepository.save(user);
        return issueTokens(user, ip, userAgent);
    }

    // -------------------------------------------------------------------------
    // KDF params (necesario antes del login para que el cliente derive su clave)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public KdfParamsResponse getKdfParams(String email) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        return new KdfParamsResponse(
                user.getKdfType(),
                user.getKdfIterations(),
                user.getKdfMemory(),
                user.getKdfParallelism(),
                user.getKdfSalt()
        );
    }

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    public AuthResponse login(LoginRequest request, String ip, String userAgent) {
        var user = userRepository.findByEmailWithRoles(request.email()).orElse(null);

        // Ejecuta un BCrypt "dummy" cuando el email no existe para igualar el tiempo de
        // respuesta con el caso de usuario existente y no filtrar qué emails están registrados.
        if (user == null) {
            passwordEncoder.matches(request.masterPasswordHash(), DUMMY_BCRYPT_HASH);
            throw new InvalidCredentialsException();
        }

        // Verificar si la cuenta está bloqueada
        if (user.getLockedUntil() != null && OffsetDateTime.now().isBefore(user.getLockedUntil())) {
            throw new AccountLockedException(user.getLockedUntil());
        }

        if (!passwordEncoder.matches(request.masterPasswordHash(), user.getMasterPasswordHash())) {
            registerFailedAttempt(user);
            throw new InvalidCredentialsException();
        }

        if (user.getTotpEnabled()) {
            if (request.totpCode() == null || request.totpCode().isBlank()) {
                throw new TotpRequiredException();
            }
            // Un código TOTP inválido también cuenta para el lockout — de lo contrario el
            // segundo factor sería fuerza-bruteable de forma ilimitada con la password ya conocida.
            try {
                verifyTotp(user, request.totpCode());
            } catch (InvalidTotpException e) {
                registerFailedAttempt(user);
                throw e;
            }
        }

        // Login exitoso — resetear contador
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        return issueTokens(user, ip, userAgent);
    }

    // -------------------------------------------------------------------------
    // Refresh token (rotación — el token viejo se revoca al emitir uno nuevo)
    // -------------------------------------------------------------------------

    public AuthResponse refresh(RefreshRequest request, String ip, String userAgent) {
        String tokenHash = hashToken(request.refreshToken());

        var refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .filter(RefreshToken::isValid)
                .orElseThrow(InvalidTokenException::new);

        var user = refreshToken.getUser();

        refreshToken.setRevoked(true);
        refreshToken.setRevokedAt(OffsetDateTime.now());
        refreshTokenRepository.save(refreshToken);

        return issueTokens(user, ip, userAgent);
    }

    // -------------------------------------------------------------------------
    // Logout
    // -------------------------------------------------------------------------

    public void logout(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            token.setRevokedAt(OffsetDateTime.now());
            refreshTokenRepository.save(token);
        });
    }

    // -------------------------------------------------------------------------
    // Sesiones activas
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SessionResponse> getSessions(UUID userId) {
        return refreshTokenRepository
                .findAllByUserIdAndRevokedFalseAndExpiresAtAfter(userId, OffsetDateTime.now())
                .stream()
                .map(t -> new SessionResponse(
                        t.getId(),
                        t.getDeviceInfo(),
                        t.getIpAddress(),
                        t.getCreatedAt(),
                        t.getExpiresAt()
                ))
                .toList();
    }

    public void revokeSession(UUID userId, UUID sessionId) {
        var token = refreshTokenRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(InvalidTokenException::new);

        token.setRevoked(true);
        token.setRevokedAt(OffsetDateTime.now());
        refreshTokenRepository.save(token);
    }

    // -------------------------------------------------------------------------
    // 2FA — Setup y verificación
    // -------------------------------------------------------------------------

    public TotpSetupResponse setupTotp(UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        String rawSecret = totpSecretGenerator.generate();

        // Cifra el secret antes de persistir — nunca texto plano en DB
        user.setTotpSecret(encryptionService.encrypt(rawSecret));
        userRepository.save(user);

        QrData qrData = new QrData.Builder()
                .label(user.getEmail())
                .secret(rawSecret)
                .issuer("IronKey")
                .digits(6)
                .period(30)
                .build();

        try {
            byte[] qrImageBytes = qrGenerator.generate(qrData);
            String qrBase64 = Base64.getEncoder().encodeToString(qrImageBytes);
            return new TotpSetupResponse(rawSecret, qrData.getUri(), qrBase64);
        } catch (QrGenerationException e) {
            throw new IronKeyException("Error al generar el código QR");
        }
    }

    public void verifyAndEnableTotp(UUID userId, String totpCode) {
        var user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getTotpSecret() == null) {
            throw new IronKeyException("Primero configura el 2FA con /2fa/setup");
        }

        verifyTotp(user, totpCode);
        user.setTotpEnabled(true);
        userRepository.save(user);
    }

    public void disableTotp(UUID userId, String totpCode) {
        var user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.getTotpEnabled()) {
            throw new IronKeyException("El 2FA no está activado");
        }

        verifyTotp(user, totpCode);
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
    }

    // -------------------------------------------------------------------------
    // Recovery (gated por feature flag IRONKEY_RECOVERY_ENABLED)
    // -------------------------------------------------------------------------

    public void setupRecovery(UUID userId, RecoverySetupRequest request) {
        if (!recoveryEnabled) throw new RecoveryNotEnabledException();

        var user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.getTotpEnabled()) {
            throw new IronKeyException("Se requiere 2FA activo para configurar la recuperación");
        }

        verifyTotp(user, request.totpCode());
        user.setRecoveryEnabled(true);
        user.setRecoveryCodeHash(hashToken(request.recoveryCode()));
        user.setRecoveryProtectedKey(request.recoveryProtectedKey());
        user.setRecoveryProtectedKeyIv(request.recoveryProtectedKeyIv());
        userRepository.save(user);
    }

    public void disableRecovery(UUID userId, String totpCode) {
        if (!recoveryEnabled) throw new RecoveryNotEnabledException();

        var user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        verifyTotp(user, totpCode);
        user.setRecoveryEnabled(false);
        user.setRecoveryCodeHash(null);
        user.setRecoveryProtectedKey(null);
        user.setRecoveryProtectedKeyIv(null);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public RecoveryDataResponse getRecoveryData(String email) {
        if (!recoveryEnabled) throw new RecoveryNotEnabledException();

        var user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.getRecoveryEnabled()) {
            throw new RecoveryNotConfiguredException();
        }

        return new RecoveryDataResponse(
                user.getRecoveryProtectedKey(),
                user.getRecoveryProtectedKeyIv(),
                user.getKdfType(),
                user.getKdfIterations(),
                user.getKdfMemory(),
                user.getKdfParallelism(),
                user.getKdfSalt()
        );
    }

    public AuthResponse recoverAccount(RecoverAccountRequest request, String ip, String userAgent) {
        if (!recoveryEnabled) throw new RecoveryNotEnabledException();

        var user = userRepository.findByEmailWithRoles(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.getRecoveryEnabled()) {
            throw new RecoveryNotConfiguredException();
        }

        if (!constantTimeEquals(hashToken(request.recoveryCode()), user.getRecoveryCodeHash())) {
            throw new InvalidRecoveryCodeException();
        }

        user.setMasterPasswordHash(passwordEncoder.encode(request.newMasterPasswordHash()));
        user.setProtectedSymmetricKey(request.newProtectedSymmetricKey());
        user.setProtectedSymmetricKeyIv(request.newProtectedSymmetricKeyIv());
        // Invalida cualquier access token ya emitido — sin esto seguiría siendo válido
        // hasta 15 min más pese a que la master password y la vault key ya cambiaron.
        user.setTokenVersion(user.getTokenVersion() + 1);

        refreshTokenRepository.revokeAllByUserId(user.getId(), OffsetDateTime.now());
        userRepository.save(user);

        return issueTokens(user, ip, userAgent);
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    // Incrementa el contador de fallos y bloquea la cuenta si supera el límite
    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= lockoutProperties.maxFailedLoginAttempts()) {
            user.setLockedUntil(
                OffsetDateTime.now().plusMinutes(lockoutProperties.lockoutDurationMinutes())
            );
        }

        userRepository.save(user);
    }

    // Descifra el secret almacenado y verifica el código TOTP
    private void verifyTotp(User user, String totpCode) {
        String rawSecret = encryptionService.decrypt(user.getTotpSecret());
        if (!codeVerifier.isValidCode(rawSecret, totpCode)) {
            throw new InvalidTotpException();
        }
    }

    private AuthResponse issueTokens(User user, String ip, String userAgent) {
        String accessToken    = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRawRefreshToken();

        var refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(rawRefreshToken))
                .ipAddress(ip)
                .deviceInfo(userAgent)
                .expiresAt(OffsetDateTime.now().plusSeconds(jwtProperties.refreshTokenExpiration() / 1000))
                .build();

        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                accessToken,
                rawRefreshToken,
                user.getProtectedSymmetricKey(),
                user.getProtectedSymmetricKeyIv(),
                user.getRequireReprompt(),
                user.getVaultTimeoutMinutes(),
                user.getRecoveryEnabled()
        );
    }

    // Comparación en tiempo constante para no filtrar información vía timing al verificar hashes.
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
