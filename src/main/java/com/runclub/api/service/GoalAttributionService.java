package com.runclub.api.service;

import com.runclub.api.entity.Activity;
import com.runclub.api.entity.ClubGoal;
import com.runclub.api.entity.ClubMembership;
import com.runclub.api.entity.GoalContribution;
import com.runclub.api.entity.User;
import com.runclub.api.repository.ActivityRepository;
import com.runclub.api.repository.ClubGoalRepository;
import com.runclub.api.repository.ClubMembershipRepository;
import com.runclub.api.repository.GoalContributionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Service
public class GoalAttributionService {
    private static final Logger logger = Logger.getLogger(GoalAttributionService.class.getName());

    private final ClubMembershipRepository membershipRepository;
    private final ClubGoalRepository goalRepository;
    private final GoalContributionRepository contributionRepository;
    private final ActivityRepository activityRepository;

    public GoalAttributionService(ClubMembershipRepository membershipRepository,
                                  ClubGoalRepository goalRepository,
                                  GoalContributionRepository contributionRepository,
                                  ActivityRepository activityRepository) {
        this.membershipRepository = membershipRepository;
        this.goalRepository = goalRepository;
        this.contributionRepository = contributionRepository;
        this.activityRepository = activityRepository;
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

        LocalDateTime startInclusive = goal.getStartDate().atStartOfDay();
        // End-exclusive at start-of-next-day so any time on the end date counts.
        LocalDateTime endExclusive = goal.getEndDate().plusDays(1).atStartOfDay();

        // Load every member of the club; cap at 200 since the live-attribution
        // path uses the same number. Realistic clubs are far smaller.
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
}
