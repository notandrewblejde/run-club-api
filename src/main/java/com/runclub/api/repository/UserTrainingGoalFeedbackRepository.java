package com.runclub.api.repository;

import com.runclub.api.entity.UserTrainingGoalFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public interface UserTrainingGoalFeedbackRepository extends JpaRepository<UserTrainingGoalFeedback, UUID> {
    Page<UserTrainingGoalFeedback> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserId(UUID userId);

    @Transactional
    int deleteByUserId(UUID userId);
}
