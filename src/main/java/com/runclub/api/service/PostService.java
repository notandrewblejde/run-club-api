package com.runclub.api.service;

import com.runclub.api.api.ApiException;
import com.runclub.api.entity.Club;
import com.runclub.api.entity.ClubMembership;
import com.runclub.api.entity.Activity;
import com.runclub.api.entity.Post;
import com.runclub.api.entity.User;
import com.runclub.api.model.ClubFeedActivityItem;
import com.runclub.api.model.ClubFeedItem;
import com.runclub.api.model.ClubFeedPostItem;
import com.runclub.api.model.ClubFeed;
import com.runclub.api.repository.ActivityRepository;
import com.runclub.api.repository.ClubMembershipRepository;
import com.runclub.api.repository.ClubRepository;
import com.runclub.api.repository.PostRepository;
import com.runclub.api.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final ClubRepository clubRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final ClubMembershipRepository clubMembershipRepository;

    public PostService(PostRepository postRepository, ClubRepository clubRepository,
                       UserRepository userRepository, ActivityRepository activityRepository,
                       ClubMembershipRepository clubMembershipRepository) {
        this.postRepository = postRepository;
        this.clubRepository = clubRepository;
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.clubMembershipRepository = clubMembershipRepository;
    }

    public Post createPost(UUID clubId, UUID userId, String content, String[] photoUrls, UUID relatedActivityId) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> ApiException.notFound("club"));
        User author = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));

        clubMembershipRepository.findByClubAndUser(club, author)
            .orElseThrow(() -> ApiException.forbidden("User is not a member of this club"));

        if (content == null || content.isBlank()) {
            throw ApiException.missingField("content");
        }

        Activity related = null;
        if (relatedActivityId != null) {
            related = activityRepository.findById(relatedActivityId)
                .orElseThrow(() -> ApiException.notFound("activity"));
            if (!related.getUser().getId().equals(author.getId())) {
                throw ApiException.forbidden("You can only link your own activities to a post");
            }
        }

        Post post = new Post();
        post.setClub(club);
        post.setAuthor(author);
        post.setContent(content);
        post.setPhotoUrls(photoUrls);
        post.setRelatedActivity(related);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());

        return postRepository.save(post);
    }

    public Post getPost(UUID clubId, UUID postId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> ApiException.notFound("post"));
        if (!post.getClub().getId().equals(clubId)) {
            throw ApiException.notFound("post");
        }
        return post;
    }

    public Post updatePost(UUID clubId, UUID postId, UUID userId, String content, String[] photoUrls) {
        Post post = getPost(clubId, postId);
        if (!post.getAuthor().getId().equals(userId)) {
            throw ApiException.forbidden("Only post author can edit");
        }
        boolean any = false;
        if (content != null) {
            String trimmed = content.trim();
            if (trimmed.isEmpty()) {
                throw ApiException.badRequest("content cannot be blank");
            }
            post.setContent(trimmed);
            any = true;
        }
        if (photoUrls != null) {
            post.setPhotoUrls(photoUrls);
            any = true;
        }
        if (!any) {
            throw ApiException.badRequest("Provide content and/or photos");
        }
        post.setUpdatedAt(LocalDateTime.now());
        return postRepository.save(post);
    }

    public void deletePost(UUID clubId, UUID postId, UUID userId) {
        Post post = getPost(clubId, postId);
        if (!post.getAuthor().getId().equals(userId)) {
            throw ApiException.forbidden("Only post author can delete");
        }
        postRepository.delete(post);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ClubFeed getClubFeed(UUID clubId, int page, int limit) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> ApiException.notFound("club"));

        Pageable postPageable = PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Post> posts = postRepository.findByClubOrderByCreatedAtDesc(club, postPageable);

        Pageable activityPageable = PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "startDate"));

        // Membership rows use joinedAt, not createdAt — invalid sort breaks this query at runtime.
        Pageable membershipPageable = PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "joinedAt"));
        java.util.List<ClubMembership> memberships = clubMembershipRepository.findByClub(club, membershipPageable).getContent();
        java.util.List<User> clubMembers = memberships.stream().map(ClubMembership::getUser).collect(Collectors.toList());

        Page<Activity> activities = activityRepository.findByUserInOrderByStartDateDesc(clubMembers, activityPageable);

        List<ClubFeedItem> feed = new ArrayList<>();

        for (Post post : posts.getContent()) {
            feed.add(ClubFeedPostItem.from(post));
        }

        for (Activity activity : activities.getContent()) {
            feed.add(ClubFeedActivityItem.from(activity));
        }

        feed.sort(Comparator.comparing(
            item -> item.getCreatedAt() != null ? item.getCreatedAt() : LocalDateTime.MIN,
            Comparator.reverseOrder()));

        int startIdx = Math.max(0, (page - 1) * limit);
        int endIdx = Math.min(startIdx + limit, feed.size());

        return ClubFeed.builder()
            .feed(feed.subList(startIdx, endIdx))
            .total(feed.size())
            .page(page)
            .limit(limit)
            .build();
    }
}
