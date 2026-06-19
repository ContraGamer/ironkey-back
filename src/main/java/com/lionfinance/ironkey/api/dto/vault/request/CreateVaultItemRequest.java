package com.lionfinance.ironkey.api.dto.vault.request;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateVaultItemRequest(

        // JSON cifrado en cliente: {name, url, username, password, notes, tags}
        @NotBlank
        String encryptedData,

        // IV (nonce) aleatorio generado por el cliente para este ítem
        @NotBlank
        String iv,

        // Nullable — si es null el ítem queda en el vault raíz sin carpeta
        UUID folderId
) {}
