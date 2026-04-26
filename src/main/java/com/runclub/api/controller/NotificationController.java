package com.runclub.api.controller;

import com.runclub.api.api.ApiList;
import com.runclub.api.api.Auth;
import com.runclub.api.entity.UserNotification;
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
import java.util.Map;
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
    public Map<String, Object> preview(Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        return notificationService.preview(userId);
    }

    @GetMapping
    public ApiList<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        Page<UserNotification> p = notificationService.list(userId, page, limit);
        List<Map<String, Object>> data = p.getContent().stream().map(NotificationService::toMap).toList();
        return ApiList.of(data, p.hasNext(), p.getTotalElements(), "/v1/notifications");
    }

    @PatchMapping("/{id}/read")
    public Map<String, Object> markRead(@PathVariable("id") UUID id, Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        UserNotification n = notificationService.markRead(userId, id);
        return NotificationService.toMap(n);
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> readAll(Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        int updated = notificationService.markAllRead(userId);
        return ResponseEntity.ok(Map.of("updated", updated));
    }
}
