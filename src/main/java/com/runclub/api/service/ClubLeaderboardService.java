package com.runclub.api.service;

import com.runclub.api.api.ApiException;
import com.runclub.api.entity.Activity;
import com.runclub.api.entity.Club;
import com.runclub.api.entity.ClubMembership;
import com.runclub.api.entity.User;
import com.runclub.api.model.LeaderboardEntry;
import com.runclub.api.repository.ActivityRepository;
import com.runclub.api.repository.ClubMembershipRepository;
import com.runclub.api.repository.ClubRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Club-level distance leaderboards: rolling 30d, all-time in club, or a specific
 * goal (delegates to {@link ClubGoalService}).
 */
@Service
public class ClubLeaderboardService {

    public static final int MAX_LIMIT = 50;

    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final ActivityRepository activityRepository;
    private final ClubGoalService clubGoalService;

    public ClubLeaderboardService(ClubRepository clubRepository,
                                  ClubMembershipRepository membershipRepository,
                                  ActivityRepository activityRepository,
                                  ClubGoalService clubGoalService) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.activityRepository = activityRepository;
        this.clubGoalService = clubGoalService;
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> getLeaderboard(UUID clubId, String window, UUID goalId, int limit) {
        int take = Math.min(Math.max(1, limit), MAX_LIMIT);
        if (goalId != null) {
            return clubGoalService.getGoalLeaderboardForClub(clubId, goalId, take);
        }
        if (window == null || window.isBlank()) {
            throw ApiException.badRequest("Missing window: use 30d, all, or pass goal_id");
        }
        return switch (window) {
            case "30d" -> leaderboardLast30Days(clubId, take);
            case "all" -> leaderboardAllTimeInClub(clubId, take);
            default -> throw ApiException.badRequest("window must be 30d or all (or use goal_id)");
        };
    }

    private List<LeaderboardEntry> leaderboardLast30Days(UUID clubId, int take) {
        Club club = clubRepository.findById(clubId).orElseThrow(() -> ApiException.notFound("club"));
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(30);
        return buildFromActivities(club, start, end, take);
    }

    /**
     * Sums each member's run distance for activities on or after their club join time.
     */
    private List<LeaderboardEntry> leaderboardAllTimeInClub(UUID clubId, int take) {
        Club club = clubRepository.findById(clubId).orElseThrow(() -> ApiException.notFound("club"));
        List<ClubMembership> memberships = membershipRepository
            .findByClub(club, PageRequest.of(0, 200))
            .getContent();
        if (memberships.isEmpty()) {
            return List.of();
        }
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime minJoin = memberships.stream()
            .map(ClubMembership::getJoinedAt)
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder())
            .orElse(LocalDateTime.of(2020, 1, 1, 0, 0)); // fallback: include all history
        return buildFromActivities(club, minJoin, end, take);
    }

    private List<LeaderboardEntry> buildFromActivities(
        Club club,
        LocalDateTime rangeStart,
        LocalDateTime rangeEnd,
        int take
    ) {
        List<ClubMembership> memberships = membershipRepository
            .findByClub(club, PageRequest.of(0, 200))
            .getContent();
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<User> users = new ArrayList<>();
        Map<UUID, LocalDateTime> joinAtByUser = new HashMap<>();
        for (ClubMembership m : memberships) {
            if (m.getUser() == null) continue;
            UUID uid = m.getUser().getId();
            users.add(m.getUser());
            if (m.getJoinedAt() != null) {
                joinAtByUser.put(uid, m.getJoinedAt());
            }
        }
        if (users.isEmpty()) {
            return List.of();
        }
        List<Activity> activities = activityRepository
            .findByUserInAndStartDateBetween(users, rangeStart, rangeEnd);

        Map<UUID, BigDecimal> totalByUserId = new HashMap<>();
        Map<UUID, User> userById = new HashMap<>();
        for (Activity a : activities) {
            if (a.getUser() == null || a.getStartDate() == null) continue;
            User u = a.getUser();
            UUID uid = u.getId();
            userById.putIfAbsent(uid, u);
            LocalDateTime join = joinAtByUser.get(uid);
            if (join != null && a.getStartDate().isBefore(join)) {
                continue;
            }
            BigDecimal miles = a.getDistanceMiles() != null ? a.getDistanceMiles() : BigDecimal.ZERO;
            totalByUserId.merge(uid, miles, BigDecimal::add);
        }
        return toRankedEntries(totalByUserId, userById, take);
    }

    private List<LeaderboardEntry> toRankedEntries(
        Map<UUID, BigDecimal> totalByUserId,
        Map<UUID, User> userById,
        int take
    ) {
        // Include all members (even 0 miles) so the leaderboard always shows the full roster
        // Ensure every member is represented, even if they have no runs in the window
        for (User u : users) {
            totalByUserId.putIfAbsent(u.getId(), BigDecimal.ZERO);
            userById.putIfAbsent(u.getId(), u);
        }
        List<Map.Entry<UUID, BigDecimal>> sorted = totalByUserId.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(take)
            .collect(Collectors.toList());

        List<LeaderboardEntry> out = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<UUID, BigDecimal> e : sorted) {
            User u = userById.get(e.getKey());
            if (u == null) continue;
            LeaderboardEntry entry = new LeaderboardEntry();
            entry.rank = rank++;
            entry.user = com.runclub.api.model.User.from(u);
            entry.totalDistanceMiles = e.getValue();
            out.add(entry);
        }
        return out;
    }
}
