package com.runclub.api.service;

import com.runclub.api.entity.*;
import com.runclub.api.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ActivityEngagementService {

    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final ActivityKudoRepository kudoRepository;
    private final ActivityCommentRepository commentRepository;

    public ActivityEngagementService(ActivityRepository activityRepository,
                                     UserRepository userRepository,
                                     ActivityKudoRepository kudoRepository,
                                     ActivityCommentRepository commentRepository) {
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.kudoRepository = kudoRepository;
        this.commentRepository = commentRepository;
    }

    @Transactional
    public boolean toggleKudo(UUID activityId, UUID userId) {
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> new RuntimeException("Activity not found"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        return kudoRepository.findByActivityAndUser(activity, user)
            .map(existing -> {
                kudoRepository.delete(existing);
                activity.setKudosCount(Math.max(0, (activity.getKudosCount() == null ? 0 : activity.getKudosCount()) - 1));
                activityRepository.save(activity);
                return false;
            })
            .orElseGet(() -> {
                ActivityKudo kudo = new ActivityKudo();
                kudo.setActivity(activity);
                kudo.setUser(user);
                kudoRepository.save(kudo);
                activity.setKudosCount((activity.getKudosCount() == null ? 0 : activity.getKudosCount()) + 1);
                activityRepository.save(activity);
                return true;
            });
    }

    public boolean hasKudo(UUID activityId, UUID userId) {
        Activity activity = activityRepository.findById(activityId).orElse(null);
        User user = userRepository.findById(userId).orElse(null);
        if (activity == null || user == null) return false;
        return kudoRepository.findByActivityAndUser(activity, user).isPresent();
    }

    @Transactional
    public ActivityComment addComment(UUID activityId, UUID userId, String content) {
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Comment content cannot be empty");
        }
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> new RuntimeException("Activity not found"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        ActivityComment comment = new ActivityComment();
        comment.setActivity(activity);
        comment.setUser(user);
        comment.setContent(content.trim());
        ActivityComment saved = commentRepository.save(comment);

        activity.setCommentCount((activity.getCommentCount() == null ? 0 : activity.getCommentCount()) + 1);
        activityRepository.save(activity);

        return saved;
    }

    @Transactional
    public void deleteComment(UUID commentId, UUID userId) {
        ActivityComment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new RuntimeException("Comment not found"));
        if (!comment.getUser().getId().equals(userId)
                && !comment.getActivity().getUser().getId().equals(userId)) {
            throw new RuntimeException("Forbidden: only the comment author or activity owner can delete");
        }
        Activity activity = comment.getActivity();
        commentRepository.delete(comment);
        activity.setCommentCount(Math.max(0, (activity.getCommentCount() == null ? 0 : activity.getCommentCount()) - 1));
        activityRepository.save(activity);
    }

    public Page<ActivityComment> listComments(UUID activityId, int page, int limit) {
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> new RuntimeException("Activity not found"));
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 100));
        return commentRepository.findByActivityOrderByCreatedAtAsc(activity, pageable);
    }
}
