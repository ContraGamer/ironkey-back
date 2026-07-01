package com.lionfinance.ironkey.api.dto.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank
        @Size(max = 255)
        String masterPasswordHash,

        // Opcional: solo requerido si el usuario tiene 2FA activado
        @Size(max = 6)
        String totpCode
) {}
