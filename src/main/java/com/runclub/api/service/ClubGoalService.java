package com.runclub.api.service;

import com.runclub.api.api.ApiException;
import com.runclub.api.entity.*;
import com.runclub.api.model.GoalProgress;
import com.runclub.api.model.LeaderboardEntry;
import com.runclub.api.repository.*;
import com.runclub.api.dto.UpdateGoalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ClubGoalService {

    private final ClubGoalRepository clubGoalRepository;
    private final GoalContributionRepository goalContributionRepository;
    private final ClubRepository clubRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final ClubMembershipRepository clubMembershipRepository;
    private final GoalAttributionService goalAttributionService;

    public ClubGoalService(ClubGoalRepository clubGoalRepository,
                          GoalContributionRepository goalContributionRepository,
                          ClubRepository clubRepository,
                          UserRepository userRepository,
                          ActivityRepository activityRepository,
                          ClubMembershipRepository clubMembershipRepository,
                          GoalAttributionService goalAttributionService) {
        this.clubGoalRepository = clubGoalRepository;
        this.goalContributionRepository = goalContributionRepository;
        this.clubRepository = clubRepository;
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.clubMembershipRepository = clubMembershipRepository;
        this.goalAttributionService = goalAttributionService;
    }

    public ClubGoal createGoal(UUID clubId, String name, BigDecimal targetDistanceMiles,
                               LocalDate startDate, LocalDate endDate, UUID createdByUserId) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> ApiException.notFound("club"));
        assertAdminOrOwner(club, createdByUserId);

        ClubGoal goal = new ClubGoal();
        goal.setClub(club);
        goal.setName(name);
        goal.setTargetDistanceMiles(targetDistanceMiles);
        goal.setStartDate(startDate);
        goal.setEndDate(endDate);
        goal.setCreatedAt(LocalDateTime.now());
        goal.setUpdatedAt(LocalDateTime.now());

        ClubGoal saved = clubGoalRepository.save(goal);

        // If the goal's window opens in the past or today, credit every member's
        // already-synced activities in [start, end]. Async after commit so the
        // worker always sees the persisted goal row.
        if (startDate != null && !startDate.isAfter(LocalDate.now())) {
            final UUID newGoalId = saved.getId();
            runAfterCommit(() -> goalAttributionService.backfillContributionsAsync(newGoalId));
        }

        return saved;
    }

    public GoalContribution recordContribution(UUID goalId, UUID userId, UUID activityId) {
        ClubGoal goal = clubGoalRepository.findById(goalId)
            .orElseThrow(() -> ApiException.notFound("goal"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> ApiException.notFound("activity"));

        if (!activity.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("Activity does not belong to the user");
        }

        LocalDate activityDate = activity.getStartDate().toLocalDate();
        if (activityDate.isBefore(goal.getStartDate()) || activityDate.isAfter(goal.getEndDate())) {
            throw ApiException.badRequest("Activity is outside the goal date range");
        }

        if (goalContributionRepository.findByGoalAndActivity_Id(goal, activityId).isPresent()) {
            throw ApiException.conflict("Contribution for this activity already exists");
        }

        GoalContribution contribution = new GoalContribution();
        contribution.setGoal(goal);
        contribution.setUser(user);
        contribution.setActivity(activity);
        contribution.setDistanceMiles(activity.getDistanceMiles());
        contribution.setContributedAt(LocalDateTime.now());

        return goalContributionRepository.save(contribution);
    }

    public GoalProgress getGoalProgress(UUID goalId) {
        ClubGoal goal = clubGoalRepository.findById(goalId)
            .orElseThrow(() -> ApiException.notFound("goal"));

        BigDecimal totalContributed = goalContributionRepository.sumDistancesByGoal(goal);
        if (totalContributed == null) totalContributed = BigDecimal.ZERO;

        BigDecimal target = goal.getTargetDistanceMiles() != null ? goal.getTargetDistanceMiles() : BigDecimal.ONE;
        BigDecimal percent = totalContributed.divide(target, 2, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));

        GoalProgress p = new GoalProgress();
        p.goalId = goal.getId();
        p.name = goal.getName();
        p.targetDistanceMiles = goal.getTargetDistanceMiles();
        p.totalDistanceMiles = totalContributed;
        p.progressPercent = percent;
        p.startDate = goal.getStartDate() != null ? goal.getStartDate().toString() : null;
        p.endDate = goal.getEndDate() != null ? goal.getEndDate().toString() : null;
        return p;
    }

    public List<LeaderboardEntry> getGoalLeaderboard(UUID goalId) {
        ClubGoal goal = clubGoalRepository.findById(goalId)
            .orElseThrow(() -> ApiException.notFound("goal"));

        List<GoalContribution> contributions = goalContributionRepository.findByGoal(goal);

        Map<User, BigDecimal> userTotals = new HashMap<>();
        for (GoalContribution contrib : contributions) {
            User user = contrib.getUser();
            BigDecimal current = userTotals.getOrDefault(user, BigDecimal.ZERO);
            BigDecimal distance = contrib.getDistanceMiles() != null ? contrib.getDistanceMiles() : BigDecimal.ZERO;
            userTotals.put(user, current.add(distance));
        }

        List<Map.Entry<User, BigDecimal>> sorted = userTotals.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .toList();

        List<LeaderboardEntry> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            LeaderboardEntry e = new LeaderboardEntry();
            e.rank = i + 1;
            e.user = com.runclub.api.model.User.from(sorted.get(i).getKey());
            e.totalDistanceMiles = sorted.get(i).getValue();
            result.add(e);
        }
        return result;
    }

    public Page<ClubGoal> getActiveGoals(UUID clubId, Pageable pageable) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> ApiException.notFound("club"));
        return clubGoalRepository.findByClubAndEndDateGreaterThanEqual(club, LocalDate.now(), pageable);
    }

    public Page<ClubGoal> getAllGoals(UUID clubId, Pageable pageable) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> ApiException.notFound("club"));
        return clubGoalRepository.findByClub(club, pageable);
    }

    @Transactional
    public ClubGoal updateGoal(UUID clubId, UUID goalId, UpdateGoalRequest body, UUID userId) {
        ClubGoal goal = clubGoalRepository.findById(goalId)
            .orElseThrow(() -> ApiException.notFound("goal"));
        if (!goal.getClub().getId().equals(clubId)) {
            throw ApiException.notFound("goal");
        }
        assertAdminOrOwner(goal.getClub(), userId);

        boolean any =
            (body.name != null && !body.name.isBlank())
                || body.targetDistanceMiles != null
                || (body.startDate != null && !body.startDate.isBlank())
                || (body.endDate != null && !body.endDate.isBlank());
        if (!any) {
            throw ApiException.badRequest("Provide at least one field to update");
        }

        LocalDate oldStart = goal.getStartDate();
        LocalDate oldEnd = goal.getEndDate();

        LocalDate newStart = oldStart;
        LocalDate newEnd = oldEnd;
        if (body.startDate != null && !body.startDate.isBlank()) {
            newStart = LocalDate.parse(body.startDate);
        }
        if (body.endDate != null && !body.endDate.isBlank()) {
            newEnd = LocalDate.parse(body.endDate);
        }
        if (newEnd.isBefore(newStart)) {
            throw ApiException.badRequest("End date must be on or after start date");
        }

        if (body.name != null && !body.name.isBlank()) {
            goal.setName(body.name.trim());
        }
        if (body.targetDistanceMiles != null) {
            goal.setTargetDistanceMiles(body.targetDistanceMiles);
        }
        goal.setStartDate(newStart);
        goal.setEndDate(newEnd);
        goal.setUpdatedAt(LocalDateTime.now());

        ClubGoal saved = clubGoalRepository.save(goal);

        boolean datesChanged = !oldStart.equals(newStart) || !oldEnd.equals(newEnd);
        if (datesChanged) {
            goalAttributionService.pruneContributionsOutsideGoalWindow(saved);
            final UUID gid = saved.getId();
            runAfterCommit(() -> goalAttributionService.recomputeGoalContributionsAfterDateEditAsync(gid));
        }

        return saved;
    }

    @Transactional
    public void deleteGoal(UUID clubId, UUID goalId, UUID userId) {
        ClubGoal goal = clubGoalRepository.findById(goalId)
            .orElseThrow(() -> ApiException.notFound("goal"));
        if (!goal.getClub().getId().equals(clubId)) {
            throw ApiException.notFound("goal");
        }
        assertAdminOrOwner(goal.getClub(), userId);
        clubGoalRepository.delete(goal);
    }

    private void assertAdminOrOwner(Club club, UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));
        ClubMembership membership = clubMembershipRepository.findByClubAndUser(club, user)
            .orElseThrow(() -> ApiException.forbidden("User is not a member of this club"));
        if (!("owner".equals(membership.getRole()) || "admin".equals(membership.getRole()))) {
            throw ApiException.forbidden("Only club admins and owners can manage goals");
        }
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
