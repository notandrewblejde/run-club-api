package com.runclub.api.controller;

import com.runclub.api.api.ApiList;
import com.runclub.api.api.Auth;
import com.runclub.api.entity.UserNotification;
import com.runclub.api.model.JsonDtos.NotificationPreview;
import com.runclub.api.model.JsonDtos.NotificationReadAllResult;
import com.runclub.api.model.Notification;
import com.runclub.api.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/preview")
    public NotificationPreview preview(Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        return notificationService.preview(userId);
    }

    @GetMapping
    public ApiList<Notification> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        Page<UserNotification> p = notificationService.list(userId, page, limit);
        List<Notification> data = p.getContent().stream().map(Notification::from).toList();
        return ApiList.of(data, p.hasNext(), p.getTotalElements(), "/v1/notifications");
    }

    @PatchMapping("/{id}/read")
    public Notification markRead(@PathVariable("id") UUID id, Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        UserNotification n = notificationService.markRead(userId, id);
        return Notification.from(n);
    }

    @PostMapping("/read-all")
    public ResponseEntity<NotificationReadAllResult> readAll(Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        int updated = notificationService.markAllRead(userId);
        return ResponseEntity.ok(NotificationReadAllResult.builder().updated(updated).build());
    }
}
