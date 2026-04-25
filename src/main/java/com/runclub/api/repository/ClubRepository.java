package com.runclub.api.repository;

import com.runclub.api.entity.Club;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ClubRepository extends JpaRepository<Club, UUID> {
    Page<Club> findByPrivacyLevel(String privacyLevel, Pageable pageable);
}
