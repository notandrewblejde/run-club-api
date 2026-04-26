package com.runclub.api.security;

import com.runclub.api.service.UserProvisioningService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Provisions the local {@code users} row for the authenticated JWT principal
 * before each request reaches a controller. Runs after the OAuth2 resource
 * server has populated the {@link SecurityContextHolder}.
 *
 * <p>Skipped when there is no {@link Authentication} or it is not a
 * {@link JwtAuthenticationToken} — e.g. health endpoints, the Strava webhook,
 * or unauthenticated rejected requests.
 *
 * <p>Provisioning failures are logged but never block the request: the
 * downstream controller will surface the existing {@code notFound("user")}
 * behavior rather than an opaque 500. We'd rather degrade gracefully and have
 * loud logs than make a transient DB hiccup look like an auth failure.
 */
@Component
public class UserProvisioningFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserProvisioningFilter.class);

    private final UserProvisioningService userProvisioningService;

    public UserProvisioningFilter(UserProvisioningService userProvisioningService) {
        this.userProvisioningService = userProvisioningService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            try {
                userProvisioningService.getOrProvision(jwtAuth.getToken());
            } catch (Exception e) {
                log.error("[UserProvisioning] failed to provision user for sub={}: {}",
                    auth.getName(), e.getMessage(), e);
            }
        }
        chain.doFilter(request, response);
    }
}
