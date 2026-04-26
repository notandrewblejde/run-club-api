package com.runclub.api.repository;

import com.runclub.api.entity.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {
    Page<UserNotification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<UserNotification> findByUserIdAndTypeAndRelatedActivityId(
        UUID userId, String type, UUID relatedActivityId);

    long countByUserIdAndReadAtIsNull(UUID userId);

    Optional<UserNotification> findFirstByUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID userId);

    Optional<UserNotification> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserNotification n SET n.readAt = :readAt WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllReadForUser(@Param("userId") UUID userId, @Param("readAt") LocalDateTime readAt);
}
