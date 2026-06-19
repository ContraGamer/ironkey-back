package com.lionfinance.ironkey.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidCredentialsException extends IronKeyException {
    public InvalidCredentialsException() {
        // Mensaje genérico intencional — no revelar si el email existe o no
        super("Credenciales inválidas");
    }
}
