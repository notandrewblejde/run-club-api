package com.runclub.api.service;

import com.runclub.api.api.ApiException;
import com.runclub.api.entity.Activity;
import com.runclub.api.entity.Club;
import com.runclub.api.entity.ClubMembership;
import com.runclub.api.entity.Post;
import com.runclub.api.entity.User;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public Map<String, Object> getClubFeed(UUID clubId, int page, int limit) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> ApiException.notFound("club"));

        Pageable postPageable = PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Post> posts = postRepository.findByClubOrderByCreatedAtDesc(club, postPageable);

        Pageable activityPageable = PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "startDate"));

        java.util.List<ClubMembership> memberships = clubMembershipRepository.findByClub(club, postPageable).getContent();
        java.util.List<User> clubMembers = memberships.stream().map(ClubMembership::getUser).collect(Collectors.toList());

        Page<Activity> activities = activityRepository.findByUserInOrderByStartDateDesc(clubMembers, activityPageable);

        java.util.List<Map<String, Object>> feed = new ArrayList<>();

        for (Post post : posts.getContent()) {
            Map<String, Object> postMap = new HashMap<>();
            postMap.put("type", "post");
            postMap.put("id", post.getId());
            postMap.put("author_id", post.getAuthor().getId());
            postMap.put("author_name", post.getAuthor().getDisplayName());
            postMap.put("author_avatar_url", post.getAuthor().getProfilePicUrl());
            postMap.put("content", post.getContent());
            postMap.put("photos", post.getPhotoUrls());
            postMap.put("related_activity_id", post.getRelatedActivity() != null ? post.getRelatedActivity().getId() : null);
            postMap.put("created_at", post.getCreatedAt());
            postMap.put("updated_at", post.getUpdatedAt());
            feed.add(postMap);
        }

        for (Activity activity : activities.getContent()) {
            Map<String, Object> activityMap = new HashMap<>();
            activityMap.put("type", "activity");
            activityMap.put("id", activity.getId());
            activityMap.put("athlete_id", activity.getUser().getId());
            activityMap.put("athlete_name", activity.getUser().getDisplayName());
            activityMap.put("athlete_avatar_url", activity.getUser().getProfilePicUrl());
            activityMap.put("name", activity.getName());
            activityMap.put("sport_type", activity.getType());
            activityMap.put("distance_miles", activity.getDistanceMiles());
            activityMap.put("avg_pace_display", activity.getAvgPaceDisplay());
            activityMap.put("kudos_count", activity.getKudosCount());
            activityMap.put("comment_count", activity.getCommentCount());
            activityMap.put("created_at", activity.getStartDate());
            feed.add(activityMap);
        }

        feed.sort(Comparator.comparing(item -> {
            LocalDateTime time = (LocalDateTime) item.get("created_at");
            return time != null ? time : LocalDateTime.MIN;
        }, Comparator.reverseOrder()));

        int startIdx = Math.max(0, (page - 1) * limit);
        int endIdx = Math.min(startIdx + limit, feed.size());

        Map<String, Object> response = new HashMap<>();
        response.put("feed", feed.subList(startIdx, endIdx));
        response.put("total", feed.size());
        response.put("page", page);
        response.put("limit", limit);
        return response;
    }
}
