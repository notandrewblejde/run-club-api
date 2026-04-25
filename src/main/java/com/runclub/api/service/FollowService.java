package com.runclub.api.service;

import com.runclub.api.api.ApiException;
import com.runclub.api.entity.Activity;
import com.runclub.api.entity.Follow;
import com.runclub.api.entity.User;
import com.runclub.api.repository.ActivityRepository;
import com.runclub.api.repository.FollowRepository;
import com.runclub.api.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;

    public FollowService(FollowRepository followRepository, UserRepository userRepository, ActivityRepository activityRepository) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
    }

    public Follow followUser(UUID followerId, UUID followingId) {
        if (followerId.equals(followingId)) {
            throw ApiException.badRequest("Cannot follow yourself");
        }
        User follower = userRepository.findById(followerId)
            .orElseThrow(() -> ApiException.notFound("user"));
        User following = userRepository.findById(followingId)
            .orElseThrow(() -> ApiException.notFound("user"));

        if (followRepository.findByFollowerAndFollowing(follower, following).isPresent()) {
            throw ApiException.conflict("Already following this user");
        }

        Follow follow = new Follow();
        follow.setFollower(follower);
        follow.setFollowing(following);
        return followRepository.save(follow);
    }

    public void unfollowUser(UUID followerId, UUID followingId) {
        User follower = userRepository.findById(followerId)
            .orElseThrow(() -> ApiException.notFound("user"));
        User following = userRepository.findById(followingId)
            .orElseThrow(() -> ApiException.notFound("user"));

        Follow follow = followRepository.findByFollowerAndFollowing(follower, following)
            .orElseThrow(() -> ApiException.notFound("follow"));
        followRepository.delete(follow);
    }

    public Page<Follow> getFollowers(UUID userId, int page, int limit) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 100));
        return followRepository.findByFollowing(user, pageable);
    }

    public Page<Follow> getFollowing(UUID userId, int page, int limit) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 100));
        return followRepository.findByFollower(user, pageable);
    }

    public Page<Activity> getHomeFeed(UUID userId, int page, int limit) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));

        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 50),
            Sort.by(Sort.Direction.DESC, "startDate"));

        Page<Follow> following = followRepository.findByFollower(user, PageRequest.of(0, 1000));
        if (following.isEmpty()) {
            return Page.empty(pageable);
        }

        java.util.List<User> followingUsers = following.getContent().stream()
            .map(Follow::getFollowing)
            .toList();

        return activityRepository.findByUserInOrderByStartDateDesc(followingUsers, pageable);
    }

    public boolean isFollowing(UUID followerId, UUID followingId) {
        User follower = userRepository.findById(followerId).orElse(null);
        User following = userRepository.findById(followingId).orElse(null);
        if (follower == null || following == null) return false;
        return followRepository.findByFollowerAndFollowing(follower, following).isPresent();
    }

    public long getFollowersCount(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));
        return followRepository.countByFollowing(user);
    }

    public long getFollowingCount(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));
        return followRepository.countByFollower(user);
    }
}
