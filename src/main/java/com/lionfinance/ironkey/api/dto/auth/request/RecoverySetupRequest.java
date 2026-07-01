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
        @Size(max = 5_000)
        String recoveryProtectedKey,

        @NotBlank
        @Size(max = 255)
        String recoveryProtectedKeyIv,

        // Recovery code en texto plano generado por el cliente — el servidor almacena su hash
        @NotBlank
        @Size(min = 20, max = 200, message = "El código de recuperación debe tener entre 20 y 200 caracteres")
        String recoveryCode
) {}
