package com.runclub.api.controller;

import com.runclub.api.api.ApiList;
import com.runclub.api.api.Auth;
import com.runclub.api.model.Activity;
import com.runclub.api.model.Follow;
import com.runclub.api.service.FollowService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@Tag(name = "Follows / Feed")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping("/users/{userId}/follow")
    @ResponseStatus(HttpStatus.CREATED)
    public Follow followUser(@PathVariable UUID userId, Authentication authentication) {
        return Follow.following(followService.followUser(Auth.userId(authentication), userId));
    }

    @DeleteMapping("/users/{userId}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollowUser(@PathVariable UUID userId, Authentication authentication) {
        followService.unfollowUser(Auth.userId(authentication), userId);
    }

    @GetMapping("/users/{userId}/followers")
    public ApiList<Follow> getFollowers(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        Page<com.runclub.api.entity.Follow> followers = followService.getFollowers(userId, page, limit);
        List<Follow> data = followers.getContent().stream().map(Follow::follower).toList();
        return ApiList.of(data, followers.hasNext(), followers.getTotalElements(),
            "/v1/users/" + userId + "/followers");
    }

    @GetMapping("/users/{userId}/following")
    public ApiList<Follow> getFollowing(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        Page<com.runclub.api.entity.Follow> following = followService.getFollowing(userId, page, limit);
        List<Follow> data = following.getContent().stream().map(Follow::following).toList();
        return ApiList.of(data, following.hasNext(), following.getTotalElements(),
            "/v1/users/" + userId + "/following");
    }

    @GetMapping("/feed/home")
    public ApiList<Activity> getHomeFeed(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        UUID userId = Auth.userId(authentication);
        Page<com.runclub.api.entity.Activity> feed = followService.getHomeFeed(userId, page, limit);
        List<Activity> data = feed.getContent().stream().map(Activity::from).toList();
        return ApiList.of(data, feed.hasNext(), feed.getTotalElements(), "/v1/feed/home");
    }
}
