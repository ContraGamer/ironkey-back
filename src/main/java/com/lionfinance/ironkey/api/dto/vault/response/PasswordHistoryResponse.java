package com.lionfinance.ironkey.api.dto.vault.response;

import java.time.OffsetDateTime;

public record PasswordHistoryResponse(
        Long id,
        String encryptedData,
        String iv,
        OffsetDateTime createdAt
) {}
