package com.lionfinance.ironkey.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ironkey.security")
public record LockoutProperties(
        int maxFailedLoginAttempts,
        int lockoutDurationMinutes
) {}
