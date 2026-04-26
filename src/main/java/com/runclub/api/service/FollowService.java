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

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;

    public FollowService(FollowRepository followRepository,
                         UserRepository userRepository,
                         ActivityRepository activityRepository) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
    }

    /**
     * Initiate a follow. Public targets get an immediate accepted follow;
     * private targets get a pending follow that the target must approve.
     */
    public Follow followUser(UUID followerId, UUID followingId) {
        if (followerId.equals(followingId)) {
            throw ApiException.badRequest("Cannot follow yourself");
        }
        User follower = userRepository.findById(followerId)
            .orElseThrow(() -> ApiException.notFound("user"));
        User target = userRepository.findById(followingId)
            .orElseThrow(() -> ApiException.notFound("user"));

        if (followRepository.findByFollowerAndFollowing(follower, target).isPresent()) {
            throw ApiException.conflict("Already following or requested");
        }

        Follow follow = new Follow();
        follow.setFollower(follower);
        follow.setFollowing(target);
        follow.setStatus("private".equals(target.getPrivacyLevel()) ? STATUS_PENDING : STATUS_ACCEPTED);
        return followRepository.save(follow);
    }

    /**
     * Removes the follow regardless of state — works for "unfollow" (accepted)
     * and "cancel request" (pending) since both are the same row.
     */
    public void unfollowUser(UUID followerId, UUID followingId) {
        User follower = userRepository.findById(followerId)
            .orElseThrow(() -> ApiException.notFound("user"));
        User following = userRepository.findById(followingId)
            .orElseThrow(() -> ApiException.notFound("user"));

        Follow follow = followRepository.findByFollowerAndFollowing(follower, following)
            .orElseThrow(() -> ApiException.notFound("follow"));
        followRepository.delete(follow);
    }

    public Follow acceptRequest(UUID viewerId, UUID followId) {
        Follow follow = followRepository.findById(followId)
            .orElseThrow(() -> ApiException.notFound("follow_request"));
        if (!follow.getFollowing().getId().equals(viewerId)) {
            throw ApiException.forbidden("Only the target can accept a follow request");
        }
        if (!STATUS_PENDING.equals(follow.getStatus())) {
            throw ApiException.badRequest("Request is not pending");
        }
        follow.setStatus(STATUS_ACCEPTED);
        return followRepository.save(follow);
    }

    public void rejectRequest(UUID viewerId, UUID followId) {
        Follow follow = followRepository.findById(followId)
            .orElseThrow(() -> ApiException.notFound("follow_request"));
        if (!follow.getFollowing().getId().equals(viewerId)) {
            throw ApiException.forbidden("Only the target can reject a follow request");
        }
        followRepository.delete(follow);
    }

    public Page<Follow> getPendingRequestsFor(UUID userId, int page, int limit) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 100));
        return followRepository.findByFollowingAndStatus(user, STATUS_PENDING, pageable);
    }

    /** Followers (people following this user, accepted only). */
    public Page<Follow> getFollowers(UUID userId, int page, int limit) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 100));
        return followRepository.findByFollowingAndStatus(user, STATUS_ACCEPTED, pageable);
    }

    /** People this user follows (accepted only). */
    public Page<Follow> getFollowing(UUID userId, int page, int limit) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 100));
        return followRepository.findByFollowerAndStatus(user, STATUS_ACCEPTED, pageable);
    }

    public Page<Activity> getHomeFeed(UUID userId, int page, int limit) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));

        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 50),
            Sort.by(Sort.Direction.DESC, "startDate"));

        // Only accepted follows contribute to the home feed.
        Page<Follow> following = followRepository.findByFollowerAndStatus(
            user, STATUS_ACCEPTED, PageRequest.of(0, 1000));
        if (following.isEmpty()) {
            return Page.empty(pageable);
        }
        java.util.List<User> followingUsers = following.getContent().stream()
            .map(Follow::getFollowing)
            .toList();
        return activityRepository.findByUserInOrderByStartDateDesc(followingUsers, pageable);
    }

    /** Returns the relationship status between a viewer and a target user. */
    public String getFollowStatus(UUID viewerId, UUID targetId) {
        if (viewerId == null || viewerId.equals(targetId)) return "self";
        User viewer = userRepository.findById(viewerId).orElse(null);
        User target = userRepository.findById(targetId).orElse(null);
        if (viewer == null || target == null) return "none";
        return followRepository.findByFollowerAndFollowing(viewer, target)
            .map(Follow::getStatus) // "pending" | "accepted"
            .orElse("none");
    }

    public boolean isAcceptedFollower(UUID followerId, UUID followingId) {
        User follower = userRepository.findById(followerId).orElse(null);
        User following = userRepository.findById(followingId).orElse(null);
        if (follower == null || following == null) return false;
        return followRepository.findByFollowerAndFollowing(follower, following)
            .map(f -> STATUS_ACCEPTED.equals(f.getStatus()))
            .orElse(false);
    }

    public long getFollowersCount(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));
        return followRepository.countByFollowingAndStatus(user, STATUS_ACCEPTED);
    }

    public long getFollowingCount(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));
        return followRepository.countByFollowerAndStatus(user, STATUS_ACCEPTED);
    }
}
