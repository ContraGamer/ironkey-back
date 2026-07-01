package com.lionfinance.ironkey.security.filter;

import com.lionfinance.ironkey.security.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Las preflight CORS (OPTIONS) no deben consumir cupo de rate limit.
        if (request.getMethod().equals("OPTIONS")) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String ip   = extractIp(request);

        boolean allowed = switch (path) {
            case "/api/v1/auth/login"             -> rateLimitService.tryConsumeLogin(ip);
            case "/api/v1/auth/register"          -> rateLimitService.tryConsumeRegister(ip);
            case "/api/v1/auth/recovery/recover"  -> rateLimitService.tryConsumeRecovery(ip);
            // GET públicos que exponen existencia de cuenta y parámetros KDF
            case "/api/v1/auth/kdf-params"        -> rateLimitService.tryConsumeSensitiveRead(ip);
            case "/api/v1/auth/recovery/data"     -> rateLimitService.tryConsumeSensitiveRead(ip);
            default -> true;
        };

        if (!allowed) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\"," +
                    "\"message\":\"Demasiados intentos. Espera un momento antes de volver a intentarlo.\"}"
            );
            return;
        }

        chain.doFilter(request, response);
    }

    // getRemoteAddr() ya devuelve la IP real del cliente: Tomcat resuelve X-Forwarded-For
    // vía RemoteIpValve (server.forward-headers-strategy=native) confiando solo en proxies
    // internos. Parsear el header manualmente permitiría spoofear la IP y evadir el rate limit.
    private String extractIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
