package com.runclub.api.controller;

import com.runclub.api.entity.User;
import com.runclub.api.repository.UserRepository;
import com.runclub.api.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final UserProfileService userProfileService;

    public UserController(UserRepository userRepository, UserProfileService userProfileService) {
        this.userRepository = userRepository;
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String auth0Id = jwt.getClaimAsString("sub");

            User user = userRepository.findByAuth0Id(auth0Id).orElseGet(() -> {
                // Lazy-create on first call so the rest of the API can find a row.
                User u = new User();
                u.setAuth0Id(auth0Id);
                u.setEmail(Optional.ofNullable(jwt.getClaimAsString("email")).orElse(auth0Id + "@unknown"));
                u.setDisplayName(jwt.getClaimAsString("name"));
                u.setProfilePicUrl(jwt.getClaimAsString("picture"));
                u.setId(UUID.nameUUIDFromBytes(auth0Id.getBytes()));
                return userRepository.save(u);
            });

            return ResponseEntity.ok(userProfileService.getProfile(user.getId(), user.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getById(@PathVariable UUID userId, Authentication authentication) {
        try {
            UUID requester = requesterId(authentication);
            return ResponseEntity.ok(userProfileService.getProfile(userId, requester));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String auth0Id = jwt.getClaimAsString("sub");
            User user = userRepository.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new RuntimeException("User not found"));

            if (body.containsKey("name")) user.setDisplayName(body.get("name"));
            if (body.containsKey("avatar_url")) user.setProfilePicUrl(body.get("avatar_url"));
            userRepository.save(user);
            return ResponseEntity.ok(userProfileService.getProfile(user.getId(), user.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam("q") String query,
                                    @RequestParam(defaultValue = "20") int limit) {
        try {
            String q = query == null ? "" : query.trim().toLowerCase();
            if (q.isEmpty()) return ResponseEntity.ok(Map.of("users", List.of()));

            List<User> users = userRepository.findAll().stream()
                .filter(u -> {
                    String name = u.getDisplayName() == null ? "" : u.getDisplayName().toLowerCase();
                    String email = u.getEmail() == null ? "" : u.getEmail().toLowerCase();
                    return name.contains(q) || email.contains(q);
                })
                .limit(Math.min(limit, 50))
                .toList();

            List<Map<String, Object>> result = users.stream().map(u -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", u.getId());
                m.put("name", u.getDisplayName());
                m.put("avatar_url", u.getProfilePicUrl());
                return m;
            }).toList();

            return ResponseEntity.ok(Map.of("users", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private UUID requesterId(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return UUID.nameUUIDFromBytes(jwt.getClaimAsString("sub").getBytes());
    }
}
