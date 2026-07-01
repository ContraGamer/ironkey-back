package com.lionfinance.ironkey.api.dto.auth.response;

public record AuthResponse(

        String accessToken,

        String refreshToken,

        // El cliente necesita estos dos campos para descifrar su vault key localmente
        String protectedSymmetricKey,

        String protectedSymmetricKeyIv,

        // Preferencias del usuario — la extensión y el front las leen al hacer login
        boolean requireReprompt,

        int vaultTimeoutMinutes,

        // Estado de recovery — permite que el cliente muestre el indicador sin llamada extra
        boolean recoveryEnabled
) {}
