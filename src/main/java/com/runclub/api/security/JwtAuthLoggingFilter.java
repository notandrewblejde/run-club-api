package com.runclub.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        String method = request.getMethod();
        String uri = request.getRequestURI();

        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            // Log first/last 10 chars only for security
            String tokenPreview = token.length() > 20
                ? token.substring(0, 10) + "..." + token.substring(token.length() - 10)
                : "[short]";
            log.info("[Auth] {} {} — Bearer token present: {}", method, uri, tokenPreview);

            // Log JWT header.payload (no signature) for debugging
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                try {
                    String header = new String(java.util.Base64.getUrlDecoder().decode(
                        parts[0] + "==".substring((4 - parts[0].length() % 4) % 4)));
                    String payload = new String(java.util.Base64.getUrlDecoder().decode(
                        parts[1] + "==".substring((4 - parts[1].length() % 4) % 4)));
                    log.info("[Auth] JWT header: {}", header);
                    log.info("[Auth] JWT payload (iss/aud/exp): {}", extractClaims(payload));
                } catch (Exception e) {
                    log.warn("[Auth] Could not decode JWT: {}", e.getMessage());
                }
            }
        } else if (auth == null) {
            log.info("[Auth] {} {} — No Authorization header", method, uri);
        } else {
            log.info("[Auth] {} {} — Non-Bearer auth: {}", method, uri, auth.substring(0, Math.min(20, auth.length())));
        }

        chain.doFilter(request, response);

        int status = response.getStatus();
        if (status == 401 || status == 403) {
            log.warn("[Auth] {} {} → {} (DENIED)", method, uri, status);
        } else {
            log.debug("[Auth] {} {} → {}", method, uri, status);
        }
    }

    private String extractClaims(String payload) {
        // Extract just iss, aud, exp for logging
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(payload);
            return String.format("iss=%s aud=%s exp=%s",
                node.path("iss").asText("?"),
                node.path("aud").toString(),
                node.path("exp").asText("?"));
        } catch (Exception e) {
            return payload.length() > 200 ? payload.substring(0, 200) : payload;
        }
    }
}
