package com.lionfinance.ironkey.api.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RecoverySetupRequest(

        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "El código TOTP debe tener 6 dígitos")
        String totpCode,

        // Vault key cifrada con la recovery_derived_key (cliente la genera)
        @NotBlank
        String recoveryProtectedKey,

        @NotBlank
        String recoveryProtectedKeyIv,

        // Recovery code en texto plano generado por el cliente — el servidor almacena su hash
        @NotBlank
        @Size(min = 20, message = "El código de recuperación debe tener al menos 20 caracteres")
        String recoveryCode
) {}
