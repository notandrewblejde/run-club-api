package com.runclub.api.service;

import com.runclub.api.api.ApiException;
import com.runclub.api.entity.*;
import com.runclub.api.model.GoalProgress;
import com.runclub.api.model.LeaderboardEntry;
import com.runclub.api.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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

    public ClubGoalService(ClubGoalRepository clubGoalRepository,
                          GoalContributionRepository goalContributionRepository,
                          ClubRepository clubRepository,
                          UserRepository userRepository,
                          ActivityRepository activityRepository,
                          ClubMembershipRepository clubMembershipRepository) {
        this.clubGoalRepository = clubGoalRepository;
        this.goalContributionRepository = goalContributionRepository;
        this.clubRepository = clubRepository;
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.clubMembershipRepository = clubMembershipRepository;
    }

    public ClubGoal createGoal(UUID clubId, String name, BigDecimal targetDistanceMiles,
                               LocalDate startDate, LocalDate endDate, UUID createdByUserId) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> ApiException.notFound("club"));
        User creator = userRepository.findById(createdByUserId)
            .orElseThrow(() -> ApiException.notFound("user"));

        ClubMembership membership = clubMembershipRepository.findByClubAndUser(club, creator)
            .orElseThrow(() -> ApiException.forbidden("User is not a member of this club"));

        if (!("owner".equals(membership.getRole()) || "admin".equals(membership.getRole()))) {
            throw ApiException.forbidden("Only club admins and owners can create goals");
        }

        ClubGoal goal = new ClubGoal();
        goal.setClub(club);
        goal.setName(name);
        goal.setTargetDistanceMiles(targetDistanceMiles);
        goal.setStartDate(startDate);
        goal.setEndDate(endDate);
        goal.setCreatedAt(LocalDateTime.now());
        goal.setUpdatedAt(LocalDateTime.now());

        return clubGoalRepository.save(goal);
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
}
