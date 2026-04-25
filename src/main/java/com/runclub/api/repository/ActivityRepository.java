package com.runclub.api.repository;

import com.runclub.api.entity.Activity;
import com.runclub.api.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, UUID> {
    Page<Activity> findByUserOrderByStartDateDesc(User user, Pageable pageable);
    Optional<Activity> findByStravaActivityId(Long stravaActivityId);
}
