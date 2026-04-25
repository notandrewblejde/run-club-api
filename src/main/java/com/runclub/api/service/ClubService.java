package com.runclub.api.service;

import com.runclub.api.api.ApiException;
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
            .orElseThrow(() -> ApiException.notFound("user"));

        Club club = new Club();
        club.setName(name);
        club.setDescription(description);
        club.setPrivacyLevel(privacyLevel != null ? privacyLevel : "private");
        club.setCreatedByUser(creator);
        club.setCreatedAt(LocalDateTime.now());
        club.setUpdatedAt(LocalDateTime.now());

        Club savedClub = clubRepository.save(club);

        ClubMembership ownership = new ClubMembership();
        ownership.setClub(savedClub);
        ownership.setUser(creator);
        ownership.setRole("owner");
        ownership.setJoinedAt(LocalDateTime.now());
        membershipRepository.save(ownership);

        return savedClub;
    }

    public com.runclub.api.model.Club getClub(UUID clubId, UUID viewerId) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> ApiException.notFound("club"));

        com.runclub.api.model.Club dto = com.runclub.api.model.Club.from(club);
        dto.memberCount = membershipRepository.countByClub(club);
        if (viewerId != null) {
            User viewer = userRepository.findById(viewerId).orElse(null);
            if (viewer != null) {
                membershipRepository.findByClubAndUser(club, viewer)
                    .ifPresent(m -> dto.viewerRole = m.getRole());
            }
        }
        return dto;
    }

    public ClubMembership joinClub(UUID clubId, UUID userId) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> ApiException.notFound("club"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));

        if (membershipRepository.findByClubAndUser(club, user).isPresent()) {
            throw ApiException.conflict("User is already a member of this club");
        }
        if ("private".equals(club.getPrivacyLevel())) {
            throw ApiException.forbidden("Cannot join private clubs without an invitation");
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
            .orElseThrow(() -> ApiException.notFound("club"));
        User invitedUser = userRepository.findById(invitedUserId)
            .orElseThrow(() -> ApiException.notFound("user"));
        User invitedBy = userRepository.findById(invitedByUserId)
            .orElseThrow(() -> ApiException.notFound("user"));

        ClubMembership inviterMembership = membershipRepository.findByClubAndUser(club, invitedBy)
            .orElseThrow(() -> ApiException.forbidden("Inviter is not a member of this club"));

        if (!isAdminOrOwner(inviterMembership)) {
            throw ApiException.forbidden("Only club admins and owners can invite users");
        }
        if (membershipRepository.findByClubAndUser(club, invitedUser).isPresent()) {
            throw ApiException.conflict("User is already a member of this club");
        }

        ClubMembership membership = new ClubMembership();
        membership.setClub(club);
        membership.setUser(invitedUser);
        membership.setRole("member");
        membership.setJoinedAt(LocalDateTime.now());

        return membershipRepository.save(membership);
    }

    public ClubMembership updateMemberRole(UUID clubId, UUID userId, String newRole, UUID updatedByUserId) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> ApiException.notFound("club"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));
        User updatedBy = userRepository.findById(updatedByUserId)
            .orElseThrow(() -> ApiException.notFound("user"));

        ClubMembership updaterMembership = membershipRepository.findByClubAndUser(club, updatedBy)
            .orElseThrow(() -> ApiException.forbidden("Updater is not a member of this club"));

        if (!"owner".equals(updaterMembership.getRole())) {
            throw ApiException.forbidden("Only club owners can change member roles");
        }

        ClubMembership membership = membershipRepository.findByClubAndUser(club, user)
            .orElseThrow(() -> ApiException.notFound("club_membership"));

        membership.setRole(newRole);
        return membershipRepository.save(membership);
    }

    public void removeUserFromClub(UUID clubId, UUID userId, UUID removedByUserId) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> ApiException.notFound("club"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));
        User removedBy = userRepository.findById(removedByUserId)
            .orElseThrow(() -> ApiException.notFound("user"));

        ClubMembership removerMembership = membershipRepository.findByClubAndUser(club, removedBy)
            .orElseThrow(() -> ApiException.forbidden("Remover is not a member of this club"));

        if (!isAdminOrOwner(removerMembership)) {
            throw ApiException.forbidden("Only club admins and owners can remove members");
        }

        if ("owner".equals(removerMembership.getRole()) && userId.equals(removedByUserId)) {
            List<ClubMembership> owners = membershipRepository.findByClubAndRole(club, "owner");
            if (owners.size() <= 1) {
                throw ApiException.badRequest("Cannot remove the only owner from a club");
            }
        }

        ClubMembership membership = membershipRepository.findByClubAndUser(club, user)
            .orElseThrow(() -> ApiException.notFound("club_membership"));

        membershipRepository.delete(membership);
    }

    public Page<Club> getPublicClubs(Pageable pageable) {
        return clubRepository.findByPrivacyLevel("public", pageable);
    }

    public Page<ClubMembership> getUserClubs(UUID userId, Pageable pageable) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));
        return membershipRepository.findByUser(user, pageable);
    }

    public Page<ClubMembership> getClubMembers(UUID clubId, Pageable pageable) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> ApiException.notFound("club"));
        return membershipRepository.findByClub(club, pageable);
    }

    private static boolean isAdminOrOwner(ClubMembership m) {
        return "owner".equals(m.getRole()) || "admin".equals(m.getRole());
    }
}
