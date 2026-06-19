package com.lionfinance.ironkey.api.dto.auth.request;

import jakarta.validation.constraints.*;

public record RegisterRequest(

        @NotBlank @Email
        String email,

        // Hash KDF del master_password — nunca el password en claro
        @NotBlank
        String masterPasswordHash,

        // Parámetros KDF para que el cliente pueda derivar su clave en logins futuros
        @NotBlank
        String kdfSalt,

        @NotBlank
        String kdfType,

        @Min(1) @Max(10)
        int kdfIterations,

        @Min(16384)
        int kdfMemory,

        @Min(1) @Max(16)
        int kdfParallelism,

        // Vault key cifrada con master_derived_key (cliente la genera antes de enviar)
        @NotBlank
        String protectedSymmetricKey,

        @NotBlank
        String protectedSymmetricKeyIv
) {}
