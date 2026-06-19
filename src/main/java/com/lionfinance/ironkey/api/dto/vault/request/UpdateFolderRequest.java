package com.lionfinance.ironkey.api.dto.vault.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateFolderRequest(

        @NotBlank
        String encryptedName,

        // Nuevo IV — debe ser aleatorio en cada actualización
        @NotBlank
        String iv
) {}
