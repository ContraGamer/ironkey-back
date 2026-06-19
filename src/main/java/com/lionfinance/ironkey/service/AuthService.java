package com.lionfinance.ironkey.service;

import com.lionfinance.ironkey.api.dto.auth.request.*;
import com.lionfinance.ironkey.api.dto.auth.response.*;
import com.lionfinance.ironkey.domain.entity.RefreshToken;
import com.lionfinance.ironkey.domain.entity.User;
import com.lionfinance.ironkey.domain.repository.RefreshTokenRepository;
import com.lionfinance.ironkey.domain.repository.RoleRepository;
import com.lionfinance.ironkey.domain.repository.UserRepository;
import com.lionfinance.ironkey.exception.*;
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
    private final PasswordEncoder passwordEncoder;
    private final SecretGenerator totpSecretGenerator;
    private final QrGenerator qrGenerator;
    private final CodeVerifier codeVerifier;

    @Value("${ironkey.recovery.enabled}")
    private boolean recoveryEnabled;

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
        var user = userRepository.findByEmailWithRoles(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.masterPasswordHash(), user.getMasterPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (user.getTotpEnabled()) {
            if (request.totpCode() == null || request.totpCode().isBlank()) {
                throw new TotpRequiredException();
            }
            if (!codeVerifier.isValidCode(user.getTotpSecret(), request.totpCode())) {
                throw new InvalidTotpException();
            }
        }

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

        String secret = totpSecretGenerator.generate();

        // Almacena el secret temporalmente — TOTP queda desactivado hasta que el usuario verifique
        user.setTotpSecret(secret);
        userRepository.save(user);

        QrData qrData = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer("IronKey")
                .digits(6)
                .period(30)
                .build();

        try {
            byte[] qrImageBytes = qrGenerator.generate(qrData);
            String qrBase64 = Base64.getEncoder().encodeToString(qrImageBytes);

            return new TotpSetupResponse(secret, qrData.getUri(), qrBase64);
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

        if (!codeVerifier.isValidCode(user.getTotpSecret(), totpCode)) {
            throw new InvalidTotpException();
        }

        user.setTotpEnabled(true);
        userRepository.save(user);
    }

    public void disableTotp(UUID userId, String totpCode) {
        var user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.getTotpEnabled()) {
            throw new IronKeyException("El 2FA no está activado");
        }

        if (!codeVerifier.isValidCode(user.getTotpSecret(), totpCode)) {
            throw new InvalidTotpException();
        }

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

        if (!codeVerifier.isValidCode(user.getTotpSecret(), request.totpCode())) {
            throw new InvalidTotpException();
        }

        user.setRecoveryEnabled(true);
        user.setRecoveryProtectedKey(request.recoveryProtectedKey());
        user.setRecoveryProtectedKeyIv(request.recoveryProtectedKeyIv());
        userRepository.save(user);
    }

    public void disableRecovery(UUID userId, String totpCode) {
        if (!recoveryEnabled) throw new RecoveryNotEnabledException();

        var user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!codeVerifier.isValidCode(user.getTotpSecret(), totpCode)) {
            throw new InvalidTotpException();
        }

        // Los datos de recovery quedan dormidos en DB — no se borran
        user.setRecoveryEnabled(false);
        userRepository.save(user);
    }

    public AuthResponse recoverAccount(RecoverAccountRequest request, String ip, String userAgent) {
        if (!recoveryEnabled) throw new RecoveryNotEnabledException();

        var user = userRepository.findByEmailWithRoles(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.getRecoveryEnabled()) {
            throw new IronKeyException("Este usuario no tiene recuperación configurada");
        }

        if (!codeVerifier.isValidCode(user.getTotpSecret(), request.totpCode())) {
            throw new InvalidTotpException();
        }

        // Actualiza el master_password_hash y la vault key envuelta con el nuevo master_derived_key
        user.setMasterPasswordHash(passwordEncoder.encode(request.newMasterPasswordHash()));
        user.setProtectedSymmetricKey(request.newProtectedSymmetricKey());
        user.setProtectedSymmetricKeyIv(request.newProtectedSymmetricKeyIv());

        // Revoca todas las sesiones activas — el usuario debe volver a autenticarse
        refreshTokenRepository.revokeAllByUserId(user.getId(), OffsetDateTime.now());
        userRepository.save(user);

        return issueTokens(user, ip, userAgent);
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    private AuthResponse issueTokens(User user, String ip, String userAgent) {
        String accessToken = jwtService.generateAccessToken(user);
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
                user.getProtectedSymmetricKeyIv()
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
