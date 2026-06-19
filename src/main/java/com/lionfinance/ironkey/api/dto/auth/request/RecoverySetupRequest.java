package com.lionfinance.ironkey.api.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RecoverySetupRequest(

        // Verificación de identidad antes de configurar recovery
        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "El código TOTP debe tener 6 dígitos")
        String totpCode,

        // Vault key cifrada con la recovery_derived_key (cliente la genera)
        @NotBlank
        String recoveryProtectedKey,

        @NotBlank
        String recoveryProtectedKeyIv
) {}
