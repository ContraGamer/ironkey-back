package com.lionfinance.ironkey.api.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TotpVerifyRequest(

        // Código de 6 dígitos generado por Google Authenticator / Aegis
        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "El código TOTP debe tener 6 dígitos")
        String totpCode
) {}
