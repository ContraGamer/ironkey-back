package com.lionfinance.ironkey.api.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final int status;
    private final String error;
    private final String message;
    private final String path;

    // Código semántico opcional — presente solo en errores con identificador estable (ej. TOTP_REQUIRED)
    private final String code;

    @Builder.Default
    private final String timestamp = Instant.now().toString();

    // Solo presente en errores de validación — omitido en el resto
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final Map<String, String> fieldErrors;
}
