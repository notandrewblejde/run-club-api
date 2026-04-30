package com.runclub.api.service;

import com.runclub.api.entity.Activity;
import com.runclub.api.entity.Club;
import com.runclub.api.entity.ClubGoal;
import com.runclub.api.entity.ClubMembership;
import com.runclub.api.entity.GoalContribution;
import com.runclub.api.entity.User;
import com.runclub.api.repository.ActivityRepository;
import com.runclub.api.repository.ClubGoalRepository;
import com.runclub.api.repository.ClubMembershipRepository;
import com.runclub.api.repository.ClubRepository;
import com.runclub.api.repository.GoalContributionRepository;
import com.runclub.api.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * When an activity is synced from Strava, automatically count it toward any
 * active club goals where the user is a member and the activity's date falls
 * inside the goal's window. Idempotent — duplicate calls per activity are
 * ignored thanks to the unique (goal_id, activity_id) constraint.
 *
 * <p>Goal credit uses only each goal's {@code [startDate, endDate]} and the
 * activity's date — never {@code club_memberships.joined_at}. Late joiners get
 * full-window credit via {@link #backfillClubGoalsForNewMemberAsync}.
 */
@Service
public class GoalAttributionService {
    private static final Logger logger = Logger.getLogger(GoalAttributionService.class.getName());

    private final ClubMembershipRepository membershipRepository;
    private final ClubGoalRepository goalRepository;
    private final GoalContributionRepository contributionRepository;
    private final ActivityRepository activityRepository;
    private final ClubRepository clubRepository;
    private final UserRepository userRepository;

    public GoalAttributionService(ClubMembershipRepository membershipRepository,
                                  ClubGoalRepository goalRepository,
                                  GoalContributionRepository contributionRepository,
                                  ActivityRepository activityRepository,
                                  ClubRepository clubRepository,
                                  UserRepository userRepository) {
        this.membershipRepository = membershipRepository;
        this.goalRepository = goalRepository;
        this.contributionRepository = contributionRepository;
        this.activityRepository = activityRepository;
        this.clubRepository = clubRepository;
        this.userRepository = userRepository;
    }

    public void attributeToActiveGoals(Activity activity) {
        if (activity == null || activity.getStartDate() == null) return;
        LocalDate activityDate = activity.getStartDate().toLocalDate();

        // Walk every club the user is a member of; for each, find active goals
        // that contain this activity's date and record a contribution.
        var memberships = membershipRepository.findByUser(
            activity.getUser(),
            org.springframework.data.domain.PageRequest.of(0, 200)
        ).getContent();

        for (ClubMembership m : memberships) {
            var activeGoals = goalRepository.findByClubAndEndDateGreaterThanEqual(
                m.getClub(), LocalDate.now());
            for (ClubGoal goal : activeGoals) {
                if (activityDate.isBefore(goal.getStartDate()) || activityDate.isAfter(goal.getEndDate())) {
                    continue;
                }
                if (contributionRepository.findByGoalAndActivity_Id(goal, activity.getId()).isPresent()) {
                    continue; // already attributed
                }
                try {
                    GoalContribution c = new GoalContribution();
                    c.setGoal(goal);
                    c.setUser(activity.getUser());
                    c.setActivity(activity);
                    c.setDistanceMiles(activity.getDistanceMiles());
                    c.setContributedAt(LocalDateTime.now());
                    contributionRepository.save(c);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Auto-attribution failed for activity "
                        + activity.getId() + " → goal " + goal.getId(), e);
                }
            }
        }
    }

    /**
     * After a user joins (or is invited into) a club, credit their already-synced
     * activities toward every <em>active</em> club goal whose date window contains
     * each activity. Join time does not clip the window — a member who joins halfway
     * through still gets the full goal period for metrics.
     */
    @Async
    @Transactional
    public void backfillClubGoalsForNewMemberAsync(UUID userId, UUID clubId) {
        User user = userRepository.findById(userId).orElse(null);
        Club club = clubRepository.findById(clubId).orElse(null);
        if (user == null || club == null) return;

        List<ClubGoal> goals = goalRepository.findByClubAndEndDateGreaterThanEqual(club, LocalDate.now());
        int totalCreated = 0;
        for (ClubGoal goal : goals) {
            totalCreated += backfillGoalContributionsForMember(goal, user);
        }
        if (totalCreated > 0) {
            logger.info("New-member goal backfill: " + totalCreated + " contribution(s) for user "
                + userId + " in club " + clubId);
        }
    }

    private int backfillGoalContributionsForMember(ClubGoal goal, User user) {
        if (goal.getStartDate() == null || goal.getEndDate() == null) return 0;
        LocalDateTime startInclusive = goal.getStartDate().atStartOfDay();
        LocalDateTime endExclusive = goal.getEndDate().plusDays(1).atStartOfDay();

        List<Activity> activities = activityRepository
            .findByUserAndStartDateBetween(user, startInclusive, endExclusive);
        int created = 0;
        for (Activity a : activities) {
            if (contributionRepository.findByGoalAndActivity_Id(goal, a.getId()).isPresent()) {
                continue;
            }
            try {
                GoalContribution c = new GoalContribution();
                c.setGoal(goal);
                c.setUser(user);
                c.setActivity(a);
                c.setDistanceMiles(a.getDistanceMiles());
                c.setContributedAt(LocalDateTime.now());
                contributionRepository.save(c);
                created++;
            } catch (Exception e) {
                logger.log(Level.WARNING, "New-member goal backfill failed for activity "
                    + a.getId() + " → goal " + goal.getId(), e);
            }
        }
        return created;
    }

    /**
     * Remove contributions whose activity date falls outside the goal's
     * current [startDate, endDate] window. Used when goal dates are edited
     * (before async recompute) and defensively inside async recompute.
     */
    @Transactional
    public void pruneContributionsOutsideGoalWindow(ClubGoal goal) {
        LocalDate start = goal.getStartDate();
        LocalDate end = goal.getEndDate();
        if (start == null || end == null) return;
        List<GoalContribution> list = contributionRepository.findByGoal(goal);
        for (GoalContribution c : list) {
            if (c.getActivity() == null || c.getActivity().getStartDate() == null) {
                continue;
            }
            LocalDate activityDate = c.getActivity().getStartDate().toLocalDate();
            if (activityDate.isBefore(start) || activityDate.isAfter(end)) {
                contributionRepository.delete(c);
            }
        }
    }

    /**
     * After goal dates change: prune stragglers, align stored miles with current
     * activity rows, then backfill any missing attributions in the window.
     * Runs async and should be scheduled after the goal update transaction commits
     * so the worker sees the final dates.
     */
    @Async
    @Transactional
    public void recomputeGoalContributionsAfterDateEditAsync(UUID goalId) {
        ClubGoal goal = goalRepository.findById(goalId).orElse(null);
        if (goal == null || goal.getStartDate() == null || goal.getEndDate() == null) return;

        pruneContributionsOutsideGoalWindow(goal);
        syncContributionDistancesFromActivities(goal);

        if (!goal.getStartDate().isAfter(LocalDate.now())) {
            backfillContributionsForGoal(goal);
        }
    }

    private void syncContributionDistancesFromActivities(ClubGoal goal) {
        for (GoalContribution c : contributionRepository.findByGoal(goal)) {
            Activity a = c.getActivity();
            if (a == null) continue;
            BigDecimal fromActivity = a.getDistanceMiles();
            if (fromActivity == null) continue;
            if (c.getDistanceMiles() == null || c.getDistanceMiles().compareTo(fromActivity) != 0) {
                c.setDistanceMiles(fromActivity);
                contributionRepository.save(c);
            }
        }
    }

    /**
     * Backfill contributions for a freshly-created goal whose window includes
     * dates in the past. Walks every member's already-synced activities inside
     * [startDate, endDate] and credits any that aren't already attributed.
     *
     * Runs async because clubs with many members and a year-long window can
     * touch a lot of rows; we don't want to block the create-goal HTTP call.
     * Same idempotency guarantee as live attribution — duplicate contributions
     * are filtered by the unique (goal_id, activity_id) constraint.
     */
    @Async
    @Transactional
    public void backfillContributionsAsync(UUID goalId) {
        ClubGoal goal = goalRepository.findById(goalId).orElse(null);
        if (goal == null || goal.getStartDate() == null || goal.getEndDate() == null) return;
        backfillContributionsForGoal(goal);
    }

    private void backfillContributionsForGoal(ClubGoal goal) {
        LocalDateTime startInclusive = goal.getStartDate().atStartOfDay();
        LocalDateTime endExclusive = goal.getEndDate().plusDays(1).atStartOfDay();

        List<ClubMembership> memberships = membershipRepository
            .findByClub(goal.getClub(), PageRequest.of(0, 200))
            .getContent();
        if (memberships.isEmpty()) return;

        List<User> users = new ArrayList<>(memberships.size());
        for (ClubMembership m : memberships) users.add(m.getUser());

        List<Activity> activities = activityRepository
            .findByUserInAndStartDateBetween(users, startInclusive, endExclusive);

        int created = 0;
        for (Activity a : activities) {
            if (contributionRepository.findByGoalAndActivity_Id(goal, a.getId()).isPresent()) {
                continue;
            }
            try {
                GoalContribution c = new GoalContribution();
                c.setGoal(goal);
                c.setUser(a.getUser());
                c.setActivity(a);
                c.setDistanceMiles(a.getDistanceMiles());
                c.setContributedAt(LocalDateTime.now());
                contributionRepository.save(c);
                created++;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Backfill attribution failed for activity "
                    + a.getId() + " → goal " + goal.getId(), e);
            }
        }
        logger.info("Backfilled " + created + " contribution(s) for goal " + goal.getId());
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteContributionsByActivity(java.util.UUID activityId) {
        contributionRepository.deleteByActivity_Id(activityId);
    }

}