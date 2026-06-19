package com.lionfinance.ironkey.api.dto.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank @Email
        String email,

        @NotBlank
        String masterPasswordHash,

        // Opcional: solo requerido si el usuario tiene 2FA activado
        String totpCode
) {}
