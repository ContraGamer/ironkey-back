package com.lionfinance.ironkey.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidTotpException extends IronKeyException {
    public InvalidTotpException() {
        super("Código 2FA inválido o expirado");
    }
}
