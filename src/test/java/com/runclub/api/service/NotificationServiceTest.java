package com.runclub.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runclub.api.dto.ActivityArrivedCopy;
import com.runclub.api.entity.Activity;
import com.runclub.api.entity.User;
import com.runclub.api.entity.UserNotification;
import com.runclub.api.repository.ActivityRepository;
import com.runclub.api.repository.UserNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private UserNotificationRepository notificationRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private AthleteIntelligenceService athleteIntelligenceService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
            notificationRepository, activityRepository, athleteIntelligenceService, objectMapper);
    }

    @Test
    void createActivityArrivedNotification_insertsWhenNone() {
        UUID userId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);

        Activity activity = new Activity();
        activity.setId(activityId);
        activity.setUser(user);
        activity.setName("Morning Run");

        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(notificationRepository.findByUserIdAndTypeAndRelatedActivityId(
            userId, UserNotification.TYPE_ACTIVITY_ARRIVED, activityId)).thenReturn(Optional.empty());
        when(athleteIntelligenceService.activityArrivedNotificationCopy(activity))
            .thenReturn(new ActivityArrivedCopy("Great run", "Hydrate and rest well."));

        notificationService.createActivityArrivedNotification(userId, activityId);

        ArgumentCaptor<UserNotification> cap = ArgumentCaptor.forClass(UserNotification.class);
        verify(notificationRepository).save(cap.capture());
        UserNotification saved = cap.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals(activityId, saved.getRelatedActivityId());
        assertEquals("Great run", saved.getTitle());
        assertTrue(saved.getBody().contains("Hydrate"));
    }

    @Test
    void createActivityArrivedNotification_updatesExistingRow() {
        UUID userId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);

        Activity activity = new Activity();
        activity.setId(activityId);
        activity.setUser(user);

        UserNotification existing = new UserNotification();
        existing.setUserId(userId);
        existing.setRelatedActivityId(activityId);
        existing.setTitle("Old");

        when(activityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(notificationRepository.findByUserIdAndTypeAndRelatedActivityId(
            userId, UserNotification.TYPE_ACTIVITY_ARRIVED, activityId)).thenReturn(Optional.of(existing));
        when(athleteIntelligenceService.activityArrivedNotificationCopy(activity))
            .thenReturn(new ActivityArrivedCopy("Updated", "New body"));

        notificationService.createActivityArrivedNotification(userId, activityId);

        verify(notificationRepository).save(any(UserNotification.class));
        assertEquals("Updated", existing.getTitle());
        assertEquals("New body", existing.getBody());
    }
}
