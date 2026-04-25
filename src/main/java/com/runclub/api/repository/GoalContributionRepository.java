package com.runclub.api.repository;

import com.runclub.api.entity.ClubGoal;
import com.runclub.api.entity.GoalContribution;
import com.runclub.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalContributionRepository extends JpaRepository<GoalContribution, UUID> {
    List<GoalContribution> findByGoal(ClubGoal goal);
    List<GoalContribution> findByGoalAndUser(ClubGoal goal, User user);
    Optional<GoalContribution> findByGoalAndActivity_Id(ClubGoal goal, UUID activityId);

    @Query("SELECT SUM(gc.distanceMiles) FROM GoalContribution gc WHERE gc.goal = ?1")
    BigDecimal sumDistancesByGoal(ClubGoal goal);

    @Query("SELECT SUM(gc.distanceMiles) FROM GoalContribution gc WHERE gc.goal = ?1 AND gc.user = ?2")
    BigDecimal sumDistancesByGoalAndUser(ClubGoal goal, User user);
}
