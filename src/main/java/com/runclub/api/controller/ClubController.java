package com.runclub.api.controller;

import com.runclub.api.api.ApiList;
import com.runclub.api.api.Auth;
import com.runclub.api.dto.CreateClubRequest;
import com.runclub.api.model.Club;
import com.runclub.api.model.ClubMembership;
import com.runclub.api.model.LeaderboardEntry;
import com.runclub.api.service.ClubLeaderboardService;
import com.runclub.api.service.ClubService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/clubs")
@Tag(name = "Clubs")
public class ClubController {

    private final ClubService clubService;
    private final ClubLeaderboardService clubLeaderboardService;

    public ClubController(ClubService clubService, ClubLeaderboardService clubLeaderboardService) {
        this.clubService = clubService;
        this.clubLeaderboardService = clubLeaderboardService;
    }

    @PostMapping
    public ResponseEntity<Club> createClub(
            @Valid @RequestBody CreateClubRequest body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        com.runclub.api.entity.Club created = clubService.createClub(
            body.name, body.description, body.privacyLevel, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Club.from(created));
    }

    @GetMapping("/public")
    public ApiList<Club> listPublicClubs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 100));
        Page<com.runclub.api.entity.Club> clubs = clubService.getPublicClubs(pageable);
        List<Club> data = clubs.getContent().stream().map(Club::from).toList();
        return ApiList.of(data, clubs.hasNext(), clubs.getTotalElements(), "/v1/clubs/public");
    }

    @GetMapping("/my-clubs")
    public ApiList<Club> listMyClubs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 100));
        Page<com.runclub.api.entity.ClubMembership> memberships = clubService.getUserClubs(userId, pageable);
        List<Club> data = memberships.getContent().stream().map(m -> {
            Club c = Club.from(m.getClub());
            c.viewerRole = m.getRole();
            return c;
        }).toList();
        return ApiList.of(data, memberships.hasNext(), memberships.getTotalElements(), "/v1/clubs/my-clubs");
    }

    @GetMapping("/{clubId}")
    public Club getClub(@PathVariable UUID clubId, Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        return clubService.getClub(clubId, userId);
    }

    @GetMapping("/{clubId}/leaderboard")
    public ApiList<LeaderboardEntry> getClubLeaderboard(
            @PathVariable UUID clubId,
            @RequestParam(required = false) String window,
            @RequestParam(required = false) UUID goalId,
            @RequestParam(defaultValue = "10") int limit) {
        List<LeaderboardEntry> entries = clubLeaderboardService.getLeaderboard(clubId, window, goalId, limit);
        return ApiList.of(entries, false, entries.size(),
            "/v1/clubs/" + clubId + "/leaderboard");
    }

    @PostMapping("/{clubId}/join")
    public ClubMembership joinClub(@PathVariable UUID clubId, Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        return ClubMembership.from(clubService.joinClub(clubId, userId));
    }

    @PostMapping("/{clubId}/invitations")
    public ResponseEntity<ClubMembership> invite(
            @PathVariable UUID clubId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String invitedUserId = body.get("user_id");
        if (invitedUserId == null || invitedUserId.isEmpty()) {
            throw com.runclub.api.api.ApiException.missingField("user_id");
        }
        UUID inviterId = Auth.userId(authentication);
        UUID inviteeId = UUID.fromString(invitedUserId);
        com.runclub.api.entity.ClubMembership m = clubService.inviteUserToClub(clubId, inviteeId, inviterId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClubMembership.from(m));
    }

    @PatchMapping("/{clubId}/members/{userId}")
    public ClubMembership updateMemberRole(
            @PathVariable UUID clubId,
            @PathVariable UUID userId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        String role = body.get("role");
        if (role == null || role.isEmpty()) {
            throw com.runclub.api.api.ApiException.missingField("role");
        }
        UUID actorId = Auth.userId(authentication);
        com.runclub.api.entity.ClubMembership m = clubService.updateMemberRole(clubId, userId, role, actorId);
        return ClubMembership.from(m);
    }

    @DeleteMapping("/{clubId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @PathVariable UUID clubId,
            @PathVariable UUID userId,
            Authentication authentication) {
        UUID actorId = Auth.userId(authentication);
        clubService.removeUserFromClub(clubId, userId, actorId);
    }

    @GetMapping("/{clubId}/members")
    public ApiList<ClubMembership> listMembers(
            @PathVariable UUID clubId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 100));
        Page<com.runclub.api.entity.ClubMembership> members = clubService.getClubMembers(clubId, pageable);
        List<ClubMembership> data = members.getContent().stream().map(ClubMembership::from).toList();
        return ApiList.of(data, members.hasNext(), members.getTotalElements(),
            "/v1/clubs/" + clubId + "/members");
    }
}
