package com.lionfinance.ironkey.exception;

public class IronKeyException extends RuntimeException {
    public IronKeyException(String message) {
        super(message);
    }

    // Subclases pueden sobreescribir para exponer un código semántico estable en la respuesta JSON
    public String getCode() {
        return null;
    }
}
