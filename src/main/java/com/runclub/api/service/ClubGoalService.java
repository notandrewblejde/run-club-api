package com.runclub.api.service;

import com.runclub.api.entity.*;
import com.runclub.api.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    public ClubGoal createGoal(UUID clubId, String name, BigDecimal targetDistanceMiles, LocalDate startDate, LocalDate endDate, UUID createdByUserId) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> new RuntimeException("Club not found"));
        User creator = userRepository.findById(createdByUserId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify creator is admin or owner
        ClubMembership membership = clubMembershipRepository.findByClubAndUser(club, creator)
            .orElseThrow(() -> new RuntimeException("User is not a member of this club"));

        if (!("owner".equals(membership.getRole()) || "admin".equals(membership.getRole()))) {
            throw new RuntimeException("Only club admins and owners can create goals");
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
            .orElseThrow(() -> new RuntimeException("Goal not found"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> new RuntimeException("Activity not found"));

        // Verify activity belongs to user
        if (!activity.getUser().getId().equals(userId)) {
            throw new RuntimeException("Activity does not belong to the user");
        }

        // Check if activity is within goal date range
        LocalDate activityDate = activity.getStartDate().toLocalDate();
        if (activityDate.isBefore(goal.getStartDate()) || activityDate.isAfter(goal.getEndDate())) {
            throw new RuntimeException("Activity is outside the goal date range");
        }

        // Check if contribution already exists for this activity
        if (goalContributionRepository.findByGoalAndActivity(goal, activityId).isPresent()) {
            throw new RuntimeException("Contribution for this activity already exists");
        }

        GoalContribution contribution = new GoalContribution();
        contribution.setGoal(goal);
        contribution.setUser(user);
        contribution.setActivity(activity);
        contribution.setDistanceMiles(activity.getDistanceMiles());
        contribution.setContributedAt(LocalDateTime.now());

        return goalContributionRepository.save(contribution);
    }

    public Map<String, Object> getGoalProgress(UUID goalId) {
        ClubGoal goal = clubGoalRepository.findById(goalId)
            .orElseThrow(() -> new RuntimeException("Goal not found"));

        BigDecimal totalContributed = goalContributionRepository.sumDistancesByGoal(goal);
        if (totalContributed == null) {
            totalContributed = BigDecimal.ZERO;
        }

        BigDecimal target = goal.getTargetDistanceMiles() != null ? goal.getTargetDistanceMiles() : BigDecimal.ONE;
        BigDecimal progress = totalContributed.divide(target, 2, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));

        Map<String, Object> result = new HashMap<>();
        result.put("goal_id", goal.getId());
        result.put("name", goal.getName());
        result.put("target_miles", goal.getTargetDistanceMiles());
        result.put("total_contributed", totalContributed);
        result.put("progress_percent", progress);
        result.put("start_date", goal.getStartDate());
        result.put("end_date", goal.getEndDate());

        return result;
    }

    public List<Map<String, Object>> getGoalLeaderboard(UUID goalId) {
        ClubGoal goal = clubGoalRepository.findById(goalId)
            .orElseThrow(() -> new RuntimeException("Goal not found"));

        List<GoalContribution> contributions = goalContributionRepository.findByGoal(goal);

        Map<User, BigDecimal> userTotals = new HashMap<>();
        for (GoalContribution contrib : contributions) {
            User user = contrib.getUser();
            BigDecimal current = userTotals.getOrDefault(user, BigDecimal.ZERO);
            BigDecimal distance = contrib.getDistanceMiles() != null ? contrib.getDistanceMiles() : BigDecimal.ZERO;
            userTotals.put(user, current.add(distance));
        }

        List<Map<String, Object>> leaderboard = new ArrayList<>();
        List<Map.Entry<User, BigDecimal>> sortedEntries = userTotals.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .collect(Collectors.toList());

        for (int i = 0; i < sortedEntries.size(); i++) {
            Map.Entry<User, BigDecimal> entry = sortedEntries.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("rank", i + 1);
            item.put("user_id", entry.getKey().getId());
            item.put("display_name", entry.getKey().getDisplayName());
            item.put("email", entry.getKey().getEmail());
            item.put("total_distance_miles", entry.getValue());
            leaderboard.add(item);
        }

        return leaderboard;
    }

    public Page<ClubGoal> getActiveGoals(UUID clubId, Pageable pageable) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> new RuntimeException("Club not found"));
        return clubGoalRepository.findByClubAndEndDateGreaterThanEqual(club, LocalDate.now(), pageable);
    }

    public Page<ClubGoal> getAllGoals(UUID clubId, Pageable pageable) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> new RuntimeException("Club not found"));
        return clubGoalRepository.findByClub(club, pageable);
    }
}
