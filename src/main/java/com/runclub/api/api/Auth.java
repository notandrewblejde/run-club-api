package com.runclub.api.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** Shared helpers to lift the current user's deterministic UUID off the JWT. */
public final class Auth {
    private Auth() {}

    public static String auth0Id(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getClaimAsString("sub");
    }

    public static UUID userId(Authentication authentication) {
        return UUID.nameUUIDFromBytes(auth0Id(authentication).getBytes());
    }
}
