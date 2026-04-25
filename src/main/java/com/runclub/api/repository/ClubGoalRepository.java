package com.runclub.api.repository;

import com.runclub.api.entity.Club;
import com.runclub.api.entity.ClubGoal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ClubGoalRepository extends JpaRepository<ClubGoal, UUID> {
    Page<ClubGoal> findByClub(Club club, Pageable pageable);
    List<ClubGoal> findByClubAndEndDateGreaterThanEqual(Club club, LocalDate date);
    Page<ClubGoal> findByClubAndEndDateGreaterThanEqual(Club club, LocalDate date, Pageable pageable);
}
