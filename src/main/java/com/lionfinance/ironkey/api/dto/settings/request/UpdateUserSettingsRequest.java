package com.lionfinance.ironkey.api.dto.settings.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateUserSettingsRequest(

        // Pide la contraseña maestra antes de mostrar o copiar una credencial
        boolean requireReprompt,

        // Minutos de inactividad antes de limpiar el vault de memoria
        // 0 = nunca expira (no recomendado)
        @Min(value = 0, message = "El timeout no puede ser negativo")
        @Max(value = 10080, message = "El timeout no puede superar 7 días (10080 minutos)")
        int vaultTimeoutMinutes
) {}
