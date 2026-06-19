package com.lionfinance.ironkey.api.dto.auth.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SessionResponse(

        UUID id,
        String deviceInfo,
        String ipAddress,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {}
