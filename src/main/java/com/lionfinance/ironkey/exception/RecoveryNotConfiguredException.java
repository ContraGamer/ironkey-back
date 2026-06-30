package com.lionfinance.ironkey.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RecoveryNotConfiguredException extends IronKeyException {
    public RecoveryNotConfiguredException() {
        super("Este usuario no tiene recuperación configurada");
    }
}
