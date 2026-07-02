package com.lionfinance.ironkey.service;

import com.lionfinance.ironkey.api.dto.auth.request.LoginRequest;
import com.lionfinance.ironkey.api.dto.auth.request.RecoverAccountRequest;
import com.lionfinance.ironkey.api.dto.auth.request.RecoverySetupRequest;
import com.lionfinance.ironkey.api.dto.auth.request.RegisterRequest;
import com.lionfinance.ironkey.api.dto.auth.request.RefreshRequest;
import com.lionfinance.ironkey.domain.entity.RefreshToken;
import com.lionfinance.ironkey.domain.entity.Role;
import com.lionfinance.ironkey.domain.entity.User;
import com.lionfinance.ironkey.domain.repository.RefreshTokenRepository;
import com.lionfinance.ironkey.domain.repository.RoleRepository;
import com.lionfinance.ironkey.domain.repository.UserRepository;
import com.lionfinance.ironkey.exception.EmailAlreadyExistsException;
import com.lionfinance.ironkey.exception.InvalidCredentialsException;
import com.lionfinance.ironkey.exception.InvalidRecoveryCodeException;
import com.lionfinance.ironkey.exception.InvalidTokenException;
import com.lionfinance.ironkey.exception.TotpRequiredException;
import com.lionfinance.ironkey.exception.InvalidTotpException;
import com.lionfinance.ironkey.exception.RecoveryNotConfiguredException;
import com.lionfinance.ironkey.security.EncryptionService;
import com.lionfinance.ironkey.security.LockoutProperties;
import com.lionfinance.ironkey.security.jwt.JwtProperties;
import com.lionfinance.ironkey.security.jwt.JwtService;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Los stubs comunes de setUp() (emisión de tokens) no los usan todos los tests
// (p. ej. logout, getKdfParams, refresh con token inválido) — LENIENT evita que
// Mockito los marque como "unnecessary stubbing" en esos casos.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtService jwtService;
    @Mock JwtProperties jwtProperties;
    @Mock LockoutProperties lockoutProperties;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EncryptionService encryptionService;
    @Mock SecretGenerator totpSecretGenerator;
    @Mock QrGenerator qrGenerator;
    @Mock CodeVerifier codeVerifier;

    @InjectMocks AuthService authService;

    private User testUser;
    private Role userRole;
    private static final String IP = "127.0.0.1";
    private static final String UA = "TestBrowser/1.0";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "recoveryEnabled", false);

        userRole = Role.builder()
                .id(UUID.randomUUID())
                .name("USER")
                .build();

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@ironkey.dev")
                .masterPasswordHash("$2a$10$hashedpwd")
                .kdfSalt("salt123")
                .kdfType("argon2id")
                .kdfIterations(3)
                .kdfMemory(65536)
                .kdfParallelism(4)
                .protectedSymmetricKey("encryptedVaultKey")
                .protectedSymmetricKeyIv("vaultKeyIv")
                .totpEnabled(false)
                .recoveryEnabled(false)
                .roles(Set.of(userRole))
                .build();

        // Defaults comunes para emisión de tokens
        when(jwtService.generateAccessToken(any())).thenReturn("access.token");
        when(jwtService.generateRawRefreshToken()).thenReturn("rawRefresh");
        when(jwtProperties.refreshTokenExpiration()).thenReturn(604_800_000L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Default para tests que ejercitan registerFailedAttempt (password o TOTP inválidos)
        when(lockoutProperties.maxFailedLoginAttempts()).thenReturn(5);
        when(lockoutProperties.lockoutDurationMinutes()).thenReturn(15);

        testUser.setRequireReprompt(false);
        testUser.setVaultTimeoutMinutes(15);
    }

    // -------------------------------------------------------------------------
    // register
    // -------------------------------------------------------------------------

    @Test
    void register_newEmail_savesUserAndReturnsTokens() {
        var request = buildRegisterRequest();
        when(userRepository.existsByEmail("new@ironkey.dev")).thenReturn(false);
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("clientHash")).thenReturn("$2a$10$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = authService.register(request, IP, UA);

        assertThat(result.accessToken()).isEqualTo("access.token");
        assertThat(result.refreshToken()).isEqualTo("rawRefresh");
        assertThat(result.protectedSymmetricKey()).isEqualTo("encKey");
        verify(userRepository).save(any(User.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void register_duplicateEmail_throwsEmailAlreadyExistsException() {
        var request = buildRegisterRequest();
        when(userRepository.existsByEmail("new@ironkey.dev")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request, IP, UA))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // getKdfParams
    // -------------------------------------------------------------------------

    @Test
    void getKdfParams_existingEmail_returnsParams() {
        when(userRepository.findByEmail("user@ironkey.dev")).thenReturn(Optional.of(testUser));

        var result = authService.getKdfParams("user@ironkey.dev");

        assertThat(result.kdfType()).isEqualTo("argon2id");
        assertThat(result.kdfIterations()).isEqualTo(3);
        assertThat(result.kdfSalt()).isEqualTo("salt123");
    }

    @Test
    void getKdfParams_unknownEmail_throwsInvalidCredentialsException() {
        when(userRepository.findByEmail("ghost@ironkey.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getKdfParams("ghost@ironkey.dev"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // -------------------------------------------------------------------------
    // login
    // -------------------------------------------------------------------------

    @Test
    void login_validCredentials_returnsTokens() {
        when(userRepository.findByEmailWithRoles("user@ironkey.dev")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("clientHash", "$2a$10$hashedpwd")).thenReturn(true);

        var result = authService.login(new LoginRequest("user@ironkey.dev", "clientHash", null), IP, UA);

        assertThat(result.accessToken()).isEqualTo("access.token");
        assertThat(result.protectedSymmetricKey()).isEqualTo("encryptedVaultKey");
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentialsException() {
        when(userRepository.findByEmailWithRoles("user@ironkey.dev")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongHash", "$2a$10$hashedpwd")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@ironkey.dev", "wrongHash", null), IP, UA))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentialsException() {
        when(userRepository.findByEmailWithRoles("ghost@ironkey.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@ironkey.dev", "hash", null), IP, UA))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_totpEnabled_missingCode_throwsTotpRequiredException() {
        testUser.setTotpEnabled(true);
        testUser.setTotpSecret("ENCRYPTED_SECRET");
        when(userRepository.findByEmailWithRoles("user@ironkey.dev")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("clientHash", "$2a$10$hashedpwd")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@ironkey.dev", "clientHash", null), IP, UA))
                .isInstanceOf(TotpRequiredException.class);
    }

    @Test
    void login_totpEnabled_invalidCode_throwsInvalidTotpException() {
        testUser.setTotpEnabled(true);
        testUser.setTotpSecret("ENCRYPTED_SECRET");
        when(userRepository.findByEmailWithRoles("user@ironkey.dev")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("clientHash", "$2a$10$hashedpwd")).thenReturn(true);
        when(encryptionService.decrypt("ENCRYPTED_SECRET")).thenReturn("RAW_SECRET");
        when(codeVerifier.isValidCode("RAW_SECRET", "000000")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@ironkey.dev", "clientHash", "000000"), IP, UA))
                .isInstanceOf(InvalidTotpException.class);
    }

    @Test
    void login_totpEnabled_validCode_returnsTokens() {
        testUser.setTotpEnabled(true);
        testUser.setTotpSecret("ENCRYPTED_SECRET");
        when(userRepository.findByEmailWithRoles("user@ironkey.dev")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("clientHash", "$2a$10$hashedpwd")).thenReturn(true);
        when(encryptionService.decrypt("ENCRYPTED_SECRET")).thenReturn("RAW_SECRET");
        when(codeVerifier.isValidCode("RAW_SECRET", "123456")).thenReturn(true);

        var result = authService.login(new LoginRequest("user@ironkey.dev", "clientHash", "123456"), IP, UA);

        assertThat(result.accessToken()).isEqualTo("access.token");
    }

    // -------------------------------------------------------------------------
    // refresh
    // -------------------------------------------------------------------------

    @Test
    void refresh_validToken_revokesOldAndReturnsNew() {
        var token = buildValidRefreshToken();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        var result = authService.refresh(new RefreshRequest("rawRefresh"), IP, UA);

        assertThat(result.accessToken()).isEqualTo("access.token");
        assertThat(token.getRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void refresh_expiredToken_throwsInvalidTokenException() {
        var token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("hash")
                .revoked(false)
                .expiresAt(OffsetDateTime.now().minusDays(1))
                .build();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("rawRefresh"), IP, UA))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refresh_revokedToken_throwsInvalidTokenException() {
        var token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("hash")
                .revoked(true)
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("rawRefresh"), IP, UA))
                .isInstanceOf(InvalidTokenException.class);
    }

    // -------------------------------------------------------------------------
    // logout
    // -------------------------------------------------------------------------

    @Test
    void logout_validToken_revokesIt() {
        var token = buildValidRefreshToken();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        authService.logout("rawRefresh");

        assertThat(token.getRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void logout_unknownToken_doesNothing() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        authService.logout("unknownToken");

        verify(refreshTokenRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // setupRecovery
    // -------------------------------------------------------------------------

    @Test
    void setupRecovery_validTotpAndCode_storesCodeHash() {
        ReflectionTestUtils.setField(authService, "recoveryEnabled", true);
        testUser.setTotpEnabled(true);
        testUser.setTotpSecret("ENCRYPTED_SECRET");

        var request = new RecoverySetupRequest(
                "123456", "encRecoveryKey", "recoveryKeyIv", RECOVERY_CODE
        );

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(encryptionService.decrypt("ENCRYPTED_SECRET")).thenReturn("RAW_SECRET");
        when(codeVerifier.isValidCode("RAW_SECRET", "123456")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.setupRecovery(testUser.getId(), request);

        verify(userRepository).save(argThat(u ->
                u.getRecoveryEnabled() &&
                u.getRecoveryCodeHash().equals(sha256Base64Url(RECOVERY_CODE)) &&
                "encRecoveryKey".equals(u.getRecoveryProtectedKey())
        ));
    }

    // -------------------------------------------------------------------------
    // getRecoveryData
    // -------------------------------------------------------------------------

    @Test
    void getRecoveryData_userWithRecovery_returnsData() {
        ReflectionTestUtils.setField(authService, "recoveryEnabled", true);
        testUser.setRecoveryEnabled(true);
        testUser.setRecoveryCodeHash(sha256Base64Url(RECOVERY_CODE));
        testUser.setRecoveryProtectedKey("encRecoveryKey");
        testUser.setRecoveryProtectedKeyIv("recoveryKeyIv");

        when(userRepository.findByEmail("user@ironkey.dev")).thenReturn(Optional.of(testUser));

        var result = authService.getRecoveryData("user@ironkey.dev");

        assertThat(result.recoveryProtectedKey()).isEqualTo("encRecoveryKey");
        assertThat(result.recoveryProtectedKeyIv()).isEqualTo("recoveryKeyIv");
        assertThat(result.kdfType()).isEqualTo("argon2id");
        assertThat(result.kdfSalt()).isEqualTo("salt123");
    }

    @Test
    void getRecoveryData_recoveryNotConfigured_throwsRecoveryNotConfiguredException() {
        ReflectionTestUtils.setField(authService, "recoveryEnabled", true);
        testUser.setRecoveryEnabled(false);

        when(userRepository.findByEmail("user@ironkey.dev")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.getRecoveryData("user@ironkey.dev"))
                .isInstanceOf(RecoveryNotConfiguredException.class);
    }

    @Test
    void getRecoveryData_unknownEmail_throwsInvalidCredentialsException() {
        ReflectionTestUtils.setField(authService, "recoveryEnabled", true);

        when(userRepository.findByEmail("ghost@ironkey.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getRecoveryData("ghost@ironkey.dev"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // -------------------------------------------------------------------------
    // recoverAccount
    // -------------------------------------------------------------------------

    @Test
    void recoverAccount_validCode_updatesPasswordAndRevokesAllSessions() {
        ReflectionTestUtils.setField(authService, "recoveryEnabled", true);
        testUser.setRecoveryEnabled(true);
        testUser.setRecoveryCodeHash(sha256Base64Url(RECOVERY_CODE));

        var request = new RecoverAccountRequest(
                "user@ironkey.dev", RECOVERY_CODE, "newHashedPassword", "newProtectedKey", "newKeyIv"
        );

        when(userRepository.findByEmailWithRoles("user@ironkey.dev")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("newHashedPassword")).thenReturn("$2a$10$newEncoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = authService.recoverAccount(request, IP, UA);

        assertThat(result.accessToken()).isEqualTo("access.token");
        verify(refreshTokenRepository).revokeAllByUserId(eq(testUser.getId()), any());
        verify(userRepository).save(argThat(u ->
                "$2a$10$newEncoded".equals(u.getMasterPasswordHash()) &&
                "newProtectedKey".equals(u.getProtectedSymmetricKey())
        ));
    }

    @Test
    void recoverAccount_invalidCode_throwsInvalidRecoveryCodeException() {
        ReflectionTestUtils.setField(authService, "recoveryEnabled", true);
        testUser.setRecoveryEnabled(true);
        testUser.setRecoveryCodeHash(sha256Base64Url(RECOVERY_CODE));

        var request = new RecoverAccountRequest(
                "user@ironkey.dev", "WRONG-CODE-THAT-IS-LONG-ENOUGH", "newHash", "newKey", "newIv"
        );

        when(userRepository.findByEmailWithRoles("user@ironkey.dev")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.recoverAccount(request, IP, UA))
                .isInstanceOf(InvalidRecoveryCodeException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void recoverAccount_recoveryNotConfigured_throwsRecoveryNotConfiguredException() {
        ReflectionTestUtils.setField(authService, "recoveryEnabled", true);
        testUser.setRecoveryEnabled(false);

        var request = new RecoverAccountRequest(
                "user@ironkey.dev", RECOVERY_CODE, "newHash", "newKey", "newIv"
        );

        when(userRepository.findByEmailWithRoles("user@ironkey.dev")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.recoverAccount(request, IP, UA))
                .isInstanceOf(RecoveryNotConfiguredException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static final String RECOVERY_CODE = "ABCDEF1234567890ABCDEF12345678";

    private static String sha256Base64Url(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private RegisterRequest buildRegisterRequest() {
        return new RegisterRequest(
                "new@ironkey.dev", "clientHash", "kdfSalt",
                "argon2id", 3, 65536, 4,
                "encKey", "encIv"
        );
    }

    private RefreshToken buildValidRefreshToken() {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("hash")
                .revoked(false)
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build();
    }
}
