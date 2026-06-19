package com.lionfinance.ironkey.api.dto.common;

import java.time.OffsetDateTime;

public record HealthResponse(
        String status,
        String application,
        String version,
        OffsetDateTime timestamp
) {}
