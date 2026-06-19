package com.lionfinance.ironkey.api.dto.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RecoverAccountRequest(

        @NotBlank @Email
        String email,

        // Código TOTP actual para verificar identidad
        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "El código TOTP debe tener 6 dígitos")
        String totpCode,

        // Nuevo hash del master password (el cliente re-derivó con nuevo password)
        @NotBlank
        String newMasterPasswordHash,

        // Vault key re-cifrada con la nueva master_derived_key
        @NotBlank
        String newProtectedSymmetricKey,

        @NotBlank
        String newProtectedSymmetricKeyIv
) {}
