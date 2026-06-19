package com.lionfinance.ironkey.api.dto.vault.request;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record UpdateVaultItemRequest(

        // Re-cifrado completo del ítem — el cliente siempre envía el blob completo actualizado
        @NotBlank
        String encryptedData,

        // Nuevo IV para este cifrado (debe ser aleatorio en cada actualización)
        @NotBlank
        String iv,

        // Nullable — null mueve el ítem al vault raíz
        UUID folderId
) {}
