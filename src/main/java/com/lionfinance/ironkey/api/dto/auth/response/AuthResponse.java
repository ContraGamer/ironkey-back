package com.lionfinance.ironkey.api.dto.auth.response;

public record AuthResponse(

        String accessToken,

        String refreshToken,

        // El cliente necesita estos dos campos para descifrar su vault key localmente
        String protectedSymmetricKey,

        String protectedSymmetricKeyIv
) {}
