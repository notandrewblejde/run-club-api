package com.runclub.api.repository;

import com.runclub.api.entity.UserDailyTrainingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDailyTrainingPlanRepository extends JpaRepository<UserDailyTrainingPlan, UUID> {
    Optional<UserDailyTrainingPlan> findByUserIdAndPlanDate(UUID userId, LocalDate planDate);
}
