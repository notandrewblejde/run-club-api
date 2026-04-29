package com.runclub.api.repository;

import com.runclub.api.entity.UserPushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface UserPushTokenRepository extends JpaRepository<UserPushToken, UUID> {
    List<UserPushToken> findByUserId(UUID userId);

    @Query("SELECT t FROM UserPushToken t WHERE t.userId IN :userIds")
    List<UserPushToken> findByUserIdIn(List<UUID> userIds);

    @Modifying
    @Query("DELETE FROM UserPushToken t WHERE t.userId = :userId AND t.token = :token")
    void deleteByUserIdAndToken(UUID userId, String token);
}
