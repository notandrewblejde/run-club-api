package com.runclub.api.repository;

import com.runclub.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByAuth0Id(String auth0Id);
    Optional<User> findByEmail(String email);
    Optional<User> findByStravaAthleteId(Long stravaAthleteId);
}
