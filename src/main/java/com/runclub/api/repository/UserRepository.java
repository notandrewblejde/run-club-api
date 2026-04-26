package com.runclub.api.repository;

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
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByAuth0Id(String auth0Id);
    Optional<User> findByEmail(String email);
    Optional<User> findByStravaAthleteId(Long stravaAthleteId);

    /**
     * Case-insensitive prefix search on display name. Backed by the functional
     * btree {@code idx_users_name_lower} (V6 migration). Email is intentionally
     * not searched — substring email matching is a privacy leak and not a
     * feature the UI exposes.
     */
    @Query("select u from User u " +
           "where lower(u.displayName) like lower(concat(:q, '%')) " +
           "order by u.displayName asc")
    Page<User> searchByDisplayNamePrefix(@Param("q") String q, Pageable pageable);

    /**
     * Fallback for the suggested-users endpoint when the viewer has no
     * second-degree connections yet (e.g. brand-new account). Newest public
     * profiles, excluding the viewer. Cap via {@link Pageable}.
     */
    @Query("select u from User u " +
           "where u.privacyLevel = 'public' and u.id <> :viewer " +
           "order by u.createdAt desc")
    List<User> findRecentPublicUsersExcluding(@Param("viewer") UUID viewerId, Pageable pageable);
}
