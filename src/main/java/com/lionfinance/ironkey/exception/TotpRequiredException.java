package com.lionfinance.ironkey.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class TotpRequiredException extends IronKeyException {
    public TotpRequiredException() {
        super("Se requiere código 2FA");
    }
}
