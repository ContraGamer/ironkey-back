package com.lionfinance.ironkey.api.dto.vault.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateVaultItemRequest(

        // JSON cifrado en cliente: {name, url, username, password, notes, tags}
        @NotBlank
        @Size(max = 100_000, message = "El dato cifrado excede el tamaño máximo permitido")
        String encryptedData,

        // IV (nonce) aleatorio generado por el cliente para este ítem
        @NotBlank
        @Size(max = 64)
        String iv,

        // Nullable — si es null el ítem queda en el vault raíz sin carpeta
        UUID folderId
) {}
