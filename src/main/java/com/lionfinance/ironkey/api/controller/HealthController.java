package com.lionfinance.ironkey.api.controller;

import com.lionfinance.ironkey.api.dto.common.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${spring.application.version:0.0.1-SNAPSHOT}")
    private String applicationVersion;

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse(
                "UP",
                applicationName,
                applicationVersion,
                OffsetDateTime.now()
        );
    }
}
