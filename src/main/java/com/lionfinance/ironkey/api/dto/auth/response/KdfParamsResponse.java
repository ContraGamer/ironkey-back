package com.lionfinance.ironkey.api.dto.auth.response;

public record KdfParamsResponse(

        String kdfType,
        int kdfIterations,
        int kdfMemory,
        int kdfParallelism,

        // Salt per-usuario para que el cliente derive su master key
        String kdfSalt
) {}
