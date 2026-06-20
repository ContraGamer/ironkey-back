package com.lionfinance.ironkey.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@ResponseStatus(HttpStatus.LOCKED)
public class AccountLockedException extends IronKeyException {

    public AccountLockedException(OffsetDateTime lockedUntil) {
        super("Cuenta bloqueada temporalmente. Intenta de nuevo después de las "
                + lockedUntil.format(DateTimeFormatter.ofPattern("HH:mm")) + " UTC");
    }
}
