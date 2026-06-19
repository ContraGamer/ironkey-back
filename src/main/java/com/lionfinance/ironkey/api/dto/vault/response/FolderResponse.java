package com.lionfinance.ironkey.api.dto.vault.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FolderResponse(

        UUID id,

        // Nombre cifrado — el cliente lo descifra con su vault key
        String encryptedName,
        String iv,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
