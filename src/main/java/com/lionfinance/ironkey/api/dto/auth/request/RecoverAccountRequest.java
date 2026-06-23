package com.lionfinance.ironkey.api.dto.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecoverAccountRequest(

        @NotBlank @Email
        String email,

        // Recovery code en texto plano — el servidor verifica su hash contra el almacenado
        @NotBlank
        @Size(min = 20, message = "El código de recuperación debe tener al menos 20 caracteres")
        String recoveryCode,

        // Nuevo hash del master password (el cliente re-derivó con nuevo password)
        @NotBlank
        String newMasterPasswordHash,

        // Vault key re-cifrada con la nueva master_derived_key
        @NotBlank
        String newProtectedSymmetricKey,

        @NotBlank
        String newProtectedSymmetricKeyIv
) {}
