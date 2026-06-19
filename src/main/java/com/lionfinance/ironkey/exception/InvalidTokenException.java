package com.lionfinance.ironkey.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidTokenException extends IronKeyException {
    public InvalidTokenException() {
        super("Token inválido, expirado o revocado");
    }
}
