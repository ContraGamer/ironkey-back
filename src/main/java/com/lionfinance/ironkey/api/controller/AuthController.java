package com.lionfinance.ironkey.api.controller;

import com.lionfinance.ironkey.api.dto.auth.request.*;
import com.lionfinance.ironkey.api.dto.auth.response.*;
import com.lionfinance.ironkey.security.userdetails.IronKeyUserDetails;
import com.lionfinance.ironkey.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;

    // -------------------------------------------------------------------------
    // Registro
    // -------------------------------------------------------------------------

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request,
                                 HttpServletRequest httpRequest) {
        return authService.register(request, clientIp(httpRequest), userAgent(httpRequest));
    }

    // -------------------------------------------------------------------------
    // KDF params — llamado ANTES del login para que el cliente derive su clave
    // -------------------------------------------------------------------------

    @GetMapping("/kdf-params")
    public KdfParamsResponse getKdfParams(@RequestParam @NotBlank @Email String email) {
        return authService.getKdfParams(email);
    }

    // -------------------------------------------------------------------------
    // Login / Refresh / Logout
    // -------------------------------------------------------------------------

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest) {
        return authService.login(request, clientIp(httpRequest), userAgent(httpRequest));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request,
                                HttpServletRequest httpRequest) {
        return authService.refresh(request, clientIp(httpRequest), userAgent(httpRequest));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    // -------------------------------------------------------------------------
    // Sesiones activas
    // -------------------------------------------------------------------------

    @GetMapping("/sessions")
    public List<SessionResponse> getSessions(@AuthenticationPrincipal IronKeyUserDetails principal) {
        return authService.getSessions(principal.getUserId());
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@PathVariable UUID id,
                              @AuthenticationPrincipal IronKeyUserDetails principal) {
        authService.revokeSession(principal.getUserId(), id);
    }

    // -------------------------------------------------------------------------
    // 2FA — Setup, verificación y desactivación
    // -------------------------------------------------------------------------

    @PostMapping("/2fa/setup")
    public TotpSetupResponse setupTotp(@AuthenticationPrincipal IronKeyUserDetails principal) {
        return authService.setupTotp(principal.getUserId());
    }

    @PostMapping("/2fa/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyTotp(@Valid @RequestBody TotpVerifyRequest request,
                           @AuthenticationPrincipal IronKeyUserDetails principal) {
        authService.verifyAndEnableTotp(principal.getUserId(), request.totpCode());
    }

    @DeleteMapping("/2fa")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableTotp(@Valid @RequestBody TotpVerifyRequest request,
                            @AuthenticationPrincipal IronKeyUserDetails principal) {
        authService.disableTotp(principal.getUserId(), request.totpCode());
    }

    // -------------------------------------------------------------------------
    // Recovery — gated por feature flag del servidor
    // -------------------------------------------------------------------------

    @PostMapping("/recovery/setup")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setupRecovery(@Valid @RequestBody RecoverySetupRequest request,
                              @AuthenticationPrincipal IronKeyUserDetails principal) {
        authService.setupRecovery(principal.getUserId(), request);
    }

    @DeleteMapping("/recovery")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableRecovery(@Valid @RequestBody TotpVerifyRequest request,
                                @AuthenticationPrincipal IronKeyUserDetails principal) {
        authService.disableRecovery(principal.getUserId(), request.totpCode());
    }

    @PostMapping("/recovery/recover")
    public AuthResponse recoverAccount(@Valid @RequestBody RecoverAccountRequest request,
                                       HttpServletRequest httpRequest) {
        return authService.recoverAccount(request, clientIp(httpRequest), userAgent(httpRequest));
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For puede contener múltiples IPs — la primera es la del cliente real
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
