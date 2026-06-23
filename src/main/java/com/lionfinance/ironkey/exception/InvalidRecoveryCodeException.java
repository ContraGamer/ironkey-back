package com.lionfinance.ironkey.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidRecoveryCodeException extends IronKeyException {
    public InvalidRecoveryCodeException() {
        super("Código de recuperación inválido");
    }
}
