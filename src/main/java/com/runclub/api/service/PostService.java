package com.runclub.api.service;

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
import java.util.stream.Stream;

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

    public Post createPost(UUID clubId, UUID userId, String content, String[] photoUrls) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> new RuntimeException("Club not found"));
        User author = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        ClubMembership membership = clubMembershipRepository.findByClubAndUser(club, author)
            .orElseThrow(() -> new RuntimeException("User is not a member of this club"));

        if (content == null || content.isEmpty()) {
            throw new RuntimeException("Post content cannot be empty");
        }

        Post post = new Post();
        post.setClub(club);
        post.setAuthor(author);
        post.setContent(content);
        post.setPhotoUrls(photoUrls);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());

        return postRepository.save(post);
    }

    public void deletePost(UUID postId, UUID userId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));

        if (!post.getAuthor().getId().equals(userId)) {
            throw new RuntimeException("Only post author can delete");
        }

        postRepository.delete(post);
    }

    public Map<String, Object> getClubFeed(UUID clubId, int page, int limit) {
        Club club = clubRepository.findById(clubId)
            .orElseThrow(() -> new RuntimeException("Club not found"));

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

        int startIdx = (page - 1) * limit;
        int endIdx = Math.min(startIdx + limit, feed.size());

        Map<String, Object> response = new HashMap<>();
        response.put("feed", feed.subList(startIdx, endIdx));
        response.put("total", feed.size());
        response.put("page", page);
        response.put("limit", limit);

        return response;
    }
}
