package com.lionfinance.ironkey.api.dto.settings.response;

public record UserSettingsResponse(

        boolean requireReprompt,

        // 0 = nunca expira
        int vaultTimeoutMinutes
) {}
