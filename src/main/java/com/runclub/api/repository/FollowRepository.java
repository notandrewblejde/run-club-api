package com.runclub.api.repository;

import com.runclub.api.entity.Follow;
import com.runclub.api.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FollowRepository extends JpaRepository<Follow, UUID> {
    Optional<Follow> findByFollowerAndFollowing(User follower, User following);
    Page<Follow> findByFollower(User follower, Pageable pageable);
    Page<Follow> findByFollowing(User following, Pageable pageable);

    Page<Follow> findByFollowerAndStatus(User follower, String status, Pageable pageable);
    Page<Follow> findByFollowingAndStatus(User following, String status, Pageable pageable);

    long countByFollowing(User following);
    long countByFollower(User follower);
    long countByFollowingAndStatus(User following, String status);
    long countByFollowerAndStatus(User follower, String status);

    List<Follow> findByFollowingAndStatus(User following, String status);
}
