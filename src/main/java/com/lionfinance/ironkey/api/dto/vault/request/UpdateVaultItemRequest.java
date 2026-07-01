package com.lionfinance.ironkey.api.dto.vault.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateVaultItemRequest(

        // Re-cifrado completo del ítem — el cliente siempre envía el blob completo actualizado
        @NotBlank
        @Size(max = 100_000, message = "El dato cifrado excede el tamaño máximo permitido")
        String encryptedData,

        // Nuevo IV para este cifrado (debe ser aleatorio en cada actualización)
        @NotBlank
        @Size(max = 64)
        String iv,

        // Nullable — null mueve el ítem al vault raíz
        UUID folderId
) {}
