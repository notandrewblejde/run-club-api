package com.runclub.api.service;

import com.runclub.api.entity.Activity;
import com.runclub.api.entity.ClubGoal;
import com.runclub.api.entity.ClubMembership;
import com.runclub.api.entity.GoalContribution;
import com.runclub.api.repository.ClubGoalRepository;
import com.runclub.api.repository.ClubMembershipRepository;
import com.runclub.api.repository.GoalContributionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    public GoalAttributionService(ClubMembershipRepository membershipRepository,
                                  ClubGoalRepository goalRepository,
                                  GoalContributionRepository contributionRepository) {
        this.membershipRepository = membershipRepository;
        this.goalRepository = goalRepository;
        this.contributionRepository = contributionRepository;
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
}
