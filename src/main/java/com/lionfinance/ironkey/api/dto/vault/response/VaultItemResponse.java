package com.lionfinance.ironkey.api.dto.vault.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VaultItemResponse(

        UUID id,

        // El servidor devuelve el blob tal como lo recibió — nunca lo descifra
        String encryptedData,
        String iv,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        UUID folderId,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,

        // Solo presente en ítems de la papelera
        @JsonInclude(JsonInclude.Include.NON_NULL)
        OffsetDateTime deletedAt
) {}
