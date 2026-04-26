package com.runclub.api.repository;

import com.runclub.api.entity.Follow;
import com.runclub.api.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Friends-of-friends ranked by overlap. Returns user IDs that the viewer
     * does not already follow (or have a pending request to), ordered by the
     * number of shared connections through the accepted-follow graph.
     * Excludes the viewer themselves. Cap via the {@link Pageable} argument.
     */
    @Query("select f2.following.id " +
           "from Follow f1, Follow f2 " +
           "where f1.follower.id = :viewer " +
           "  and f1.status = 'accepted' " +
           "  and f2.follower.id = f1.following.id " +
           "  and f2.status = 'accepted' " +
           "  and f2.following.id <> :viewer " +
           "  and f2.following.id not in (" +
           "    select f3.following.id from Follow f3 where f3.follower.id = :viewer" +
           "  ) " +
           "group by f2.following.id " +
           "order by count(f2) desc")
    List<UUID> findFriendsOfFriendsIds(@Param("viewer") UUID viewerId, Pageable pageable);
}
