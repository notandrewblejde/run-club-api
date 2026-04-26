package com.runclub.api.repository;

import com.runclub.api.entity.UserTrainingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserTrainingProfileRepository extends JpaRepository<UserTrainingProfile, UUID> {
}
