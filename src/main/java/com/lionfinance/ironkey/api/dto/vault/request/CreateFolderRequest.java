package com.lionfinance.ironkey.api.dto.vault.request;

import jakarta.validation.constraints.NotBlank;

public record CreateFolderRequest(

        // Nombre de la carpeta cifrado en cliente
        @NotBlank
        String encryptedName,

        @NotBlank
        String iv
) {}
