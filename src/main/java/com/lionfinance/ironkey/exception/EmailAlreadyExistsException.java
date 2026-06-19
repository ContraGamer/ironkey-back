package com.lionfinance.ironkey.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class EmailAlreadyExistsException extends IronKeyException {
    public EmailAlreadyExistsException() {
        super("El email ya está registrado");
    }
}
