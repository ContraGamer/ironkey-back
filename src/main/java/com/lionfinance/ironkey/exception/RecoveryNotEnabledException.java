package com.lionfinance.ironkey.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class RecoveryNotEnabledException extends IronKeyException {
    public RecoveryNotEnabledException() {
        super("La recuperación de cuenta no está habilitada en este servidor");
    }
}
