package com.runclub.api.service;

import com.runclub.api.entity.Club;
import com.runclub.api.entity.ClubMembership;
import com.runclub.api.entity.User;
import com.runclub.api.repository.ClubMembershipRepository;
import com.runclub.api.repository.ClubRepository;
import com.runclub.api.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ClubService {

    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public ClubService(ClubRepository clubRepository, ClubMembershipRepository membershipRepository, UserRepository userRepository) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    public Club createClub(String name, String description, String privacyLevel, UUID creatorId) {
        User creator = userRepository.findById(creatorId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Club club = new Club();
        club.setName(name);
        club.setDescription(description);
        club.setPrivacyLevel(privacyLevel != null ? privacyLevel : "private");
        club.setCreatedByUser(creator);
        club.setCreatedAt(LocalDateTime.now());
        club.setUpdatedAt(LocalDateTime.now());

        Club savedClub = clubRepository.save(club);

        // Add creator as owner
        ClubMembership ownership = new ClubMembership();
        ownership.setClub(savedClub);
        ownership.setUser(creator);
        ownership.setRole("owner");
        ownership.setJoinedAt(LocalDateTime.now());
        membershipRepository.save(ownership);

        return savedClub;
    }

    public ClubMembership joinClub(UUID clubId, UUID userId) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> new RuntimeException("Club not found"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if already a member
        if (membershipRepository.findByClubAndUser(club, user).isPresent()) {
            throw new RuntimeException("User is already a member of this club");
        }

        // Check privacy - can only join public clubs without invitation (for now)
        if ("private".equals(club.getPrivacyLevel())) {
            throw new RuntimeException("Cannot join private clubs without an invitation");
        }

        ClubMembership membership = new ClubMembership();
        membership.setClub(club);
        membership.setUser(user);
        membership.setRole("member");
        membership.setJoinedAt(LocalDateTime.now());

        return membershipRepository.save(membership);
    }

    public ClubMembership inviteUserToClub(UUID clubId, UUID invitedUserId, UUID invitedByUserId) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> new RuntimeException("Club not found"));
        User invitedUser = userRepository.findById(invitedUserId)
            .orElseThrow(() -> new RuntimeException("Invited user not found"));
        User invitedBy = userRepository.findById(invitedByUserId)
            .orElseThrow(() -> new RuntimeException("Inviting user not found"));

        // Check if inviter is admin or owner
        ClubMembership inviterMembership = membershipRepository.findByClubAndUser(club, invitedBy)
            .orElseThrow(() -> new RuntimeException("Inviter is not a member of this club"));

        if (!("owner".equals(inviterMembership.getRole()) || "admin".equals(inviterMembership.getRole()))) {
            throw new RuntimeException("Only club admins and owners can invite users");
        }

        // Check if user is already a member
        if (membershipRepository.findByClubAndUser(club, invitedUser).isPresent()) {
            throw new RuntimeException("User is already a member of this club");
        }

        // Add user to club as member
        ClubMembership membership = new ClubMembership();
        membership.setClub(club);
        membership.setUser(invitedUser);
        membership.setRole("member");
        membership.setJoinedAt(LocalDateTime.now());

        return membershipRepository.save(membership);
    }

    public void updateMemberRole(UUID clubId, UUID userId, String newRole, UUID updatedByUserId) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> new RuntimeException("Club not found"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        User updatedBy = userRepository.findById(updatedByUserId)
            .orElseThrow(() -> new RuntimeException("Updating user not found"));

        // Check if updater is owner
        ClubMembership updaterMembership = membershipRepository.findByClubAndUser(club, updatedBy)
            .orElseThrow(() -> new RuntimeException("Updater is not a member of this club"));

        if (!"owner".equals(updaterMembership.getRole())) {
            throw new RuntimeException("Only club owners can change member roles");
        }

        ClubMembership membership = membershipRepository.findByClubAndUser(club, user)
            .orElseThrow(() -> new RuntimeException("User is not a member of this club"));

        membership.setRole(newRole);
        membershipRepository.save(membership);
    }

    public void removeUserFromClub(UUID clubId, UUID userId, UUID removedByUserId) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> new RuntimeException("Club not found"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        User removedBy = userRepository.findById(removedByUserId)
            .orElseThrow(() -> new RuntimeException("Removing user not found"));

        // Check if remover is owner or admin
        ClubMembership removerMembership = membershipRepository.findByClubAndUser(club, removedBy)
            .orElseThrow(() -> new RuntimeException("Remover is not a member of this club"));

        if (!("owner".equals(removerMembership.getRole()) || "admin".equals(removerMembership.getRole()))) {
            throw new RuntimeException("Only club admins and owners can remove members");
        }

        // Check if user is the owner - prevent removing the only owner
        if ("owner".equals(removerMembership.getRole()) && userId.equals(removedByUserId)) {
            List<ClubMembership> owners = membershipRepository.findByClubAndRole(club, "owner");
            if (owners.size() <= 1) {
                throw new RuntimeException("Cannot remove the only owner from a club");
            }
        }

        ClubMembership membership = membershipRepository.findByClubAndUser(club, user)
            .orElseThrow(() -> new RuntimeException("User is not a member of this club"));

        membershipRepository.delete(membership);
    }

    public Page<Club> getPublicClubs(Pageable pageable) {
        return clubRepository.findByPrivacyLevel("public", pageable);
    }

    public Page<ClubMembership> getUserClubs(UUID userId, Pageable pageable) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return membershipRepository.findByUser(user, pageable);
    }

    public Page<ClubMembership> getClubMembers(UUID clubId, Pageable pageable) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> new RuntimeException("Club not found"));
        return membershipRepository.findByClub(club, pageable);
    }
}
