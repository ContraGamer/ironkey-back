package com.lionfinance.ironkey.api.dto.auth.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(

        @NotBlank
        String refreshToken
) {}
