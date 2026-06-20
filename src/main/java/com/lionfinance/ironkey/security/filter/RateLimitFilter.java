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

        if (!request.getMethod().equals("POST")) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String ip   = extractIp(request);

        boolean allowed = switch (path) {
            case "/api/v1/auth/login"             -> rateLimitService.tryConsumeLogin(ip);
            case "/api/v1/auth/register"          -> rateLimitService.tryConsumeRegister(ip);
            case "/api/v1/auth/recovery/recover"  -> rateLimitService.tryConsumeRecovery(ip);
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

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
