package com.lionfinance.ironkey.api.dto.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecoverAccountRequest(

        @NotBlank @Email @Size(max = 255)
        String email,

        // Recovery code en texto plano — el servidor verifica su hash contra el almacenado
        @NotBlank
        @Size(min = 20, max = 200, message = "El código de recuperación debe tener entre 20 y 200 caracteres")
        String recoveryCode,

        // Nuevo hash del master password (el cliente re-derivó con nuevo password)
        @NotBlank
        @Size(max = 255)
        String newMasterPasswordHash,

        // Vault key re-cifrada con la nueva master_derived_key
        @NotBlank
        @Size(max = 5_000)
        String newProtectedSymmetricKey,

        @NotBlank
        @Size(max = 255)
        String newProtectedSymmetricKeyIv
) {}
