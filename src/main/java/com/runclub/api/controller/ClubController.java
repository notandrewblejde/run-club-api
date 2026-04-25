package com.runclub.api.controller;

import com.runclub.api.entity.Club;
import com.runclub.api.entity.ClubMembership;
import com.runclub.api.service.ClubService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/clubs")
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    @PostMapping
    public ResponseEntity<?> createClub(@RequestBody Map<String, String> request, Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String auth0Id = jwt.getClaimAsString("sub");
            UUID userId = UUID.nameUUIDFromBytes(auth0Id.getBytes());

            String name = request.get("name");
            String description = request.get("description");
            String privacyLevel = request.get("privacy_level");

            if (name == null || name.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Club name is required"));
            }

            Club club = clubService.createClub(name, description, privacyLevel, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("id", club.getId());
            response.put("name", club.getName());
            response.put("description", club.getDescription());
            response.put("privacy_level", club.getPrivacyLevel());
            response.put("created_at", club.getCreatedAt());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/public")
    public ResponseEntity<?> getPublicClubs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            Pageable pageable = PageRequest.of(page - 1, Math.min(limit, 100));
            Page<Club> clubs = clubService.getPublicClubs(pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("clubs", clubs.getContent());
            response.put("total", clubs.getTotalElements());
            response.put("page", clubs.getNumber() + 1);
            response.put("limit", limit);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/my-clubs")
    public ResponseEntity<?> getUserClubs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String auth0Id = jwt.getClaimAsString("sub");
            UUID userId = UUID.nameUUIDFromBytes(auth0Id.getBytes());

            Pageable pageable = PageRequest.of(page - 1, Math.min(limit, 100));
            Page<ClubMembership> memberships = clubService.getUserClubs(userId, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("clubs", memberships.map(m -> Map.of(
                "club_id", m.getClub().getId(),
                "name", m.getClub().getName(),
                "role", m.getRole(),
                "joined_at", m.getJoinedAt()
            )).getContent());
            response.put("total", memberships.getTotalElements());
            response.put("page", memberships.getNumber() + 1);
            response.put("limit", limit);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{clubId}/join")
    public ResponseEntity<?> joinClub(@PathVariable UUID clubId, Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String auth0Id = jwt.getClaimAsString("sub");
            UUID userId = UUID.nameUUIDFromBytes(auth0Id.getBytes());

            ClubMembership membership = clubService.joinClub(clubId, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("club_id", membership.getClub().getId());
            response.put("role", membership.getRole());
            response.put("joined_at", membership.getJoinedAt());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{clubId}/invite")
    public ResponseEntity<?> inviteUser(
            @PathVariable UUID clubId,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String auth0Id = jwt.getClaimAsString("sub");
            UUID invitedById = UUID.nameUUIDFromBytes(auth0Id.getBytes());

            String invitedUserId = request.get("user_id");
            if (invitedUserId == null || invitedUserId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "user_id is required"));
            }

            UUID invitedUserUuid = UUID.fromString(invitedUserId);
            ClubMembership membership = clubService.inviteUserToClub(clubId, invitedUserUuid, invitedById);

            Map<String, Object> response = new HashMap<>();
            response.put("club_id", membership.getClub().getId());
            response.put("user_id", membership.getUser().getId());
            response.put("role", membership.getRole());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{clubId}/members/{userId}/role")
    public ResponseEntity<?> updateMemberRole(
            @PathVariable UUID clubId,
            @PathVariable UUID userId,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String auth0Id = jwt.getClaimAsString("sub");
            UUID updatedById = UUID.nameUUIDFromBytes(auth0Id.getBytes());

            String newRole = request.get("role");
            if (newRole == null || newRole.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "role is required"));
            }

            clubService.updateMemberRole(clubId, userId, newRole, updatedById);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Member role updated successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{clubId}/members/{userId}")
    public ResponseEntity<?> removeMember(
            @PathVariable UUID clubId,
            @PathVariable UUID userId,
            Authentication authentication) {
        try {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String auth0Id = jwt.getClaimAsString("sub");
            UUID removedById = UUID.nameUUIDFromBytes(auth0Id.getBytes());

            clubService.removeUserFromClub(clubId, userId, removedById);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Member removed successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{clubId}/members")
    public ResponseEntity<?> getClubMembers(
            @PathVariable UUID clubId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            Pageable pageable = PageRequest.of(page - 1, Math.min(limit, 100));
            Page<ClubMembership> members = clubService.getClubMembers(clubId, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("members", members.map(m -> Map.of(
                "user_id", m.getUser().getId(),
                "email", m.getUser().getEmail(),
                "display_name", m.getUser().getDisplayName(),
                "role", m.getRole(),
                "joined_at", m.getJoinedAt()
            )).getContent());
            response.put("total", members.getTotalElements());
            response.put("page", members.getNumber() + 1);
            response.put("limit", limit);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
