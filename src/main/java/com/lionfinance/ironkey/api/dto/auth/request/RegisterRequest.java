package com.lionfinance.ironkey.api.dto.auth.request;

import jakarta.validation.constraints.*;

public record RegisterRequest(

        @NotBlank @Email @Size(max = 255)
        String email,

        // Hash KDF del master_password — nunca el password en claro
        @NotBlank
        @Size(max = 255)
        String masterPasswordHash,

        // Parámetros KDF para que el cliente pueda derivar su clave en logins futuros
        @NotBlank
        @Size(max = 255)
        String kdfSalt,

        @NotBlank
        @Size(max = 20)
        String kdfType,

        @Min(1) @Max(10)
        int kdfIterations,

        @Min(16384)
        int kdfMemory,

        @Min(1) @Max(16)
        int kdfParallelism,

        // Vault key cifrada con master_derived_key (cliente la genera antes de enviar)
        @NotBlank
        @Size(max = 5_000)
        String protectedSymmetricKey,

        @NotBlank
        @Size(max = 255)
        String protectedSymmetricKeyIv
) {}
