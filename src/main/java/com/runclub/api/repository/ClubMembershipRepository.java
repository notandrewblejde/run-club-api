package com.runclub.api.repository;

import com.runclub.api.entity.Club;
import com.runclub.api.entity.ClubMembership;
import com.runclub.api.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClubMembershipRepository extends JpaRepository<ClubMembership, UUID> {
    Optional<ClubMembership> findByClubAndUser(Club club, User user);
    Page<ClubMembership> findByUser(User user, Pageable pageable);
    Page<ClubMembership> findByClub(Club club, Pageable pageable);
    List<ClubMembership> findByClubAndRole(Club club, String role);
    int countByClub(Club club);
}
