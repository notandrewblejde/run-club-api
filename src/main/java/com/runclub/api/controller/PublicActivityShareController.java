package com.runclub.api.controller;

import com.runclub.api.entity.Activity;
import com.runclub.api.service.ActivitySharePreviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * Unauthenticated HTML previews for shared activities (Open Graph). See
 * {@link ActivitySharePreviewService#POLICY_PUBLIC_PROFILE_ONLY}.
 */
@RestController
@RequestMapping("/public")
public class PublicActivityShareController {

    private final ActivitySharePreviewService sharePreviewService;

    public PublicActivityShareController(ActivitySharePreviewService sharePreviewService) {
        this.sharePreviewService = sharePreviewService;
    }

    @GetMapping(value = "/activities/{activityId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> activityPreview(
            @PathVariable UUID activityId,
            HttpServletRequest request) {
        Optional<Activity> act = sharePreviewService.findShareableActivity(activityId);
        if (act.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.TEXT_HTML)
                .body("<!DOCTYPE html><html><head><title>Run Club</title></head><body><p>Activity not found or not shareable.</p></body></html>");
        }
        String canonical = request.getRequestURL().toString();
        String deepLink = "runclub://activity/" + activityId;
        String html = sharePreviewService.buildHtmlPage(act.get(), canonical, deepLink, activityId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
            .body(html);
    }
}
