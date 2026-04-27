package com.runclub.api.repository;

import com.runclub.api.entity.Activity;
import com.runclub.api.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    /** Loads activity and owner in one query (avoids LazyInitialization on getUser in coach chat, etc.). */
    @Query("SELECT a FROM Activity a JOIN FETCH a.user WHERE a.id = :id")
    Optional<Activity> findByIdWithUser(@Param("id") UUID id);

    Page<Activity> findByUserOrderByStartDateDesc(User user, Pageable pageable);
    Optional<Activity> findByStravaActivityId(Long stravaActivityId);
    Page<Activity> findByUserInOrderByStartDateDesc(List<User> users, Pageable pageable);

    /**
     * Activities from any of the given users whose start_date falls inside
     * [start, end] inclusive. Used by the goal backfill at creation time so
     * past-dated goals can credit existing synced activities.
     */
    List<Activity> findByUserInAndStartDateBetween(
        List<User> users, LocalDateTime start, LocalDateTime end);

    List<Activity> findByUserAndStartDateBetween(User user, LocalDateTime start, LocalDateTime end);

    List<Activity> findByUser_IdOrderByStartDateDesc(UUID userId, Pageable pageable);
}
