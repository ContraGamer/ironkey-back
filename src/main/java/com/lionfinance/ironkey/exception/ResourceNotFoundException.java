package com.lionfinance.ironkey.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends IronKeyException {
    public ResourceNotFoundException(String resource) {
        super(resource + " no encontrado");
    }
}
