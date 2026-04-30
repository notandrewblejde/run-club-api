package com.runclub.api.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Intercepts /.well-known/apple-app-site-association BEFORE Spring's context-path
 * routing and rewrites it to /api/public/link-support/apple-app-site-association.
 * This is needed because Apple fetches AASA from the root domain, but Spring Boot
 * runs under the /api context path.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WellKnownFilter implements Filter {

    @Value("${runclub.apple.team-id:}")
    private String appleTeamId;

    @Value("${runclub.ios.bundle-id:app.runclub}")
    private String iosBundleId;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        String uri = req.getRequestURI();

        if ("/.well-known/apple-app-site-association".equals(uri)) {
            HttpServletResponse resp = (HttpServletResponse) response;
            resp.setContentType("application/json");
            resp.setHeader("Cache-Control", "public, max-age=300");

            if (appleTeamId == null || appleTeamId.isBlank()) {
                resp.getWriter().write("{}");
            } else {
                String json = String.format(
                    "{\"applinks\":{\"apps\":[],\"details\":[{\"appID\":\"%s.%s\",\"paths\":[\"/api/public/activities/*\"]}]}}",
                    appleTeamId, iosBundleId);
                resp.getWriter().write(json);
            }
            return;
        }

        chain.doFilter(request, response);
    }
}
