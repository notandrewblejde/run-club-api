package com.runclub.api.service;

import com.runclub.api.entity.User;
import com.runclub.api.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Lazily creates the local users row for an authenticated Auth0 principal.
 *
 * <p>Historically, just-in-time user provisioning lived only inside
 * {@code GET /v1/users/me}. Any other authenticated endpoint that needed the
 * row (e.g. {@code POST /v1/clubs}) would fail with {@code notFound("user")}
 * if the user happened to never hit {@code /me} — for example, when {@code /me}
 * failed transiently on app launch and the client kept the session alive.
 *
 * <p>This service centralizes provisioning so a single auth-time filter can
 * guarantee the row exists for every authenticated request, and controllers can
 * just resolve the user by id.
 */
@Service
public class UserProvisioningService {

    private final UserRepository userRepository;

    public UserProvisioningService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns the {@link User} matching the JWT's {@code sub} claim, creating
     * the row on first sighting. Safe to call on every request.
     */
    @Transactional
    public User getOrProvision(Jwt jwt) {
        String auth0Id = jwt.getClaimAsString("sub");
        if (auth0Id == null || auth0Id.isBlank()) {
            throw new IllegalStateException("JWT has no sub claim");
        }

        Optional<User> existing = userRepository.findByAuth0Id(auth0Id);
        if (existing.isPresent()) {
            return existing.get();
        }

        User u = new User();
        u.setAuth0Id(auth0Id);
        u.setEmail(Optional.ofNullable(jwt.getClaimAsString("email"))
            .orElse(auth0Id + "@unknown"));
        u.setDisplayName(jwt.getClaimAsString("name"));
        u.setProfilePicUrl(jwt.getClaimAsString("picture"));
        u.setId(UUID.nameUUIDFromBytes(auth0Id.getBytes()));

        try {
            return userRepository.save(u);
        } catch (DataIntegrityViolationException race) {
            // Concurrent first-time login: another request just inserted the row
            // (auth0_id has a unique constraint). Re-fetch and return that one.
            return userRepository.findByAuth0Id(auth0Id).orElseThrow(() -> race);
        }
    }
}
