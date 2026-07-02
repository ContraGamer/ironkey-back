package com.lionfinance.ironkey.security.filter;

import com.lionfinance.ironkey.security.jwt.JwtService;
import com.lionfinance.ironkey.security.userdetails.IronKeyUserDetails;
import com.lionfinance.ironkey.security.userdetails.IronKeyUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final IronKeyUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (!jwtService.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Solo autenticamos si el contexto está vacío — evita re-procesar en la misma request
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            UUID userId = jwtService.extractUserId(token);
            UserDetails userDetails = userDetailsService.loadUserById(userId);

            // Rechaza tokens emitidos antes de un evento de invalidación (p. ej. recovery de
            // cuenta) aunque el JWT en sí siga siendo válido y no haya expirado todavía.
            int tokenVersion = jwtService.extractTokenVersion(token);
            int currentVersion = ((IronKeyUserDetails) userDetails).getUser().getTokenVersion();
            if (tokenVersion != currentVersion) {
                filterChain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}
