package com.lionfinance.ironkey.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {

    // Límites por endpoint — ajustables sin cambiar el filtro
    private static final int LOGIN_CAPACITY        = 10;   // 10 intentos por minuto por IP
    private static final int REGISTER_CAPACITY     = 5;    // 5 registros por minuto por IP
    private static final int RECOVERY_CAPACITY     = 3;    // 3 intentos por 15 minutos por IP
    private static final int READ_CAPACITY         = 20;   // 20 lecturas sensibles por minuto por IP

    // Caffeine evita que el mapa crezca indefinidamente —
    // entradas no usadas en 30 min se eliminan automáticamente
    private final Cache<String, Bucket> loginCache = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(10_000)
            .build();

    private final Cache<String, Bucket> registerCache = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(10_000)
            .build();

    private final Cache<String, Bucket> recoveryCache = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .maximumSize(5_000)
            .build();

    // Protege endpoints públicos de lectura (kdf-params, recovery/data) contra enumeración
    // masiva de usuarios y harvesting de sales KDF.
    private final Cache<String, Bucket> readCache = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(10_000)
            .build();

    public boolean tryConsumeLogin(String ip) {
        return bucketFor(loginCache, ip, LOGIN_CAPACITY, Duration.ofMinutes(1)).tryConsume(1);
    }

    public boolean tryConsumeRegister(String ip) {
        return bucketFor(registerCache, ip, REGISTER_CAPACITY, Duration.ofMinutes(1)).tryConsume(1);
    }

    public boolean tryConsumeRecovery(String ip) {
        return bucketFor(recoveryCache, ip, RECOVERY_CAPACITY, Duration.ofMinutes(15)).tryConsume(1);
    }

    public boolean tryConsumeSensitiveRead(String ip) {
        return bucketFor(readCache, ip, READ_CAPACITY, Duration.ofMinutes(1)).tryConsume(1);
    }

    private Bucket bucketFor(Cache<String, Bucket> cache, String key, int capacity, Duration period) {
        return cache.get(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, period)
                        .build())
                .build());
    }
}
