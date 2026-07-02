package com.lionfinance.ironkey.security.config;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TotpConfig {

    @Bean
    public SecretGenerator totpSecretGenerator() {
        return new DefaultSecretGenerator(32);
    }

    @Bean
    public QrGenerator qrGenerator() {
        return new ZxingPngQrGenerator();
    }

    @Bean
    public CodeVerifier codeVerifier() {
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, new SystemTimeProvider());
        // Por defecto la librería acepta el período anterior y el siguiente (discrepancy=1),
        // lo que triplica la cantidad de códigos válidos en cualquier instante y facilita
        // la fuerza bruta. 0 = solo el período actual (±0-30s), suficiente margen práctico.
        verifier.setAllowedTimePeriodDiscrepancy(0);
        return verifier;
    }
}
