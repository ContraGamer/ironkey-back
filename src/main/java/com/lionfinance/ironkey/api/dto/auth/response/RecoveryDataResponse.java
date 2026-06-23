package com.lionfinance.ironkey.api.dto.auth.response;

public record RecoveryDataResponse(
        String recoveryProtectedKey,
        String recoveryProtectedKeyIv,
        String kdfType,
        Integer kdfIterations,
        Integer kdfMemory,
        Integer kdfParallelism,
        String kdfSalt
) {}
