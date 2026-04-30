package com.runclub.api.controller;

import com.runclub.api.api.Auth;
import com.runclub.api.dto.health.HealthWorkoutImportRequest;
import com.runclub.api.entity.User;
import com.runclub.api.repository.UserRepository;
import com.runclub.api.model.JsonDtos.HealthImportResult;
import com.runclub.api.service.HealthActivityImportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/me/activities")
@Tag(name = "Me / Activities import")
public class MeActivityImportController {

    private final UserRepository userRepository;
    private final HealthActivityImportService healthActivityImportService;

    public MeActivityImportController(UserRepository userRepository,
                                      HealthActivityImportService healthActivityImportService) {
        this.userRepository = userRepository;
        this.healthActivityImportService = healthActivityImportService;
    }

    @PostMapping("/health-import")
    public ResponseEntity<HealthImportResult> importHealthWorkouts(
            @Valid @RequestBody HealthWorkoutImportRequest body,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        User user = userRepository.findById(userId).orElseThrow();

        HealthActivityImportService.ImportResult result =
            healthActivityImportService.importWorkouts(user, body.getWorkouts());

        return ResponseEntity.ok(HealthImportResult.builder()
            .imported(result.imported())
            .skipped(result.skipped())
            .build());
    }
}
