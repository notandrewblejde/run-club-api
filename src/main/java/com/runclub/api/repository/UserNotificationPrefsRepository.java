package com.runclub.api.repository;

import com.runclub.api.entity.UserNotificationPrefs;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UserNotificationPrefsRepository extends JpaRepository<UserNotificationPrefs, UUID> {}
