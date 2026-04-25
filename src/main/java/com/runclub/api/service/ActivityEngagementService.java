package com.runclub.api.service;

import com.runclub.api.api.ApiException;
import com.runclub.api.entity.Activity;
import com.runclub.api.entity.ActivityComment;
import com.runclub.api.entity.ActivityKudo;
import com.runclub.api.entity.User;
import com.runclub.api.model.Comment;
import com.runclub.api.model.Kudo;
import com.runclub.api.repository.ActivityCommentRepository;
import com.runclub.api.repository.ActivityKudoRepository;
import com.runclub.api.repository.ActivityRepository;
import com.runclub.api.repository.UserRepository;
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
    public Kudo toggleKudo(UUID activityId, UUID userId) {
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> ApiException.notFound("activity"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));

        return kudoRepository.findByActivityAndUser(activity, user)
            .map(existing -> {
                kudoRepository.delete(existing);
                int next = Math.max(0, (activity.getKudosCount() == null ? 0 : activity.getKudosCount()) - 1);
                activity.setKudosCount(next);
                activityRepository.save(activity);
                return new Kudo(activity.getId(), false, next);
            })
            .orElseGet(() -> {
                ActivityKudo kudo = new ActivityKudo();
                kudo.setActivity(activity);
                kudo.setUser(user);
                kudoRepository.save(kudo);
                int next = (activity.getKudosCount() == null ? 0 : activity.getKudosCount()) + 1;
                activity.setKudosCount(next);
                activityRepository.save(activity);
                return new Kudo(activity.getId(), true, next);
            });
    }

    public Kudo getKudoState(UUID activityId, UUID userId) {
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> ApiException.notFound("activity"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));
        boolean kudoed = kudoRepository.findByActivityAndUser(activity, user).isPresent();
        return new Kudo(activity.getId(), kudoed, activity.getKudosCount() == null ? 0 : activity.getKudosCount());
    }

    @Transactional
    public Comment addComment(UUID activityId, UUID userId, String content) {
        if (content == null || content.isBlank()) {
            throw ApiException.missingField("content");
        }
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> ApiException.notFound("activity"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("user"));

        ActivityComment comment = new ActivityComment();
        comment.setActivity(activity);
        comment.setUser(user);
        comment.setContent(content.trim());
        ActivityComment saved = commentRepository.save(comment);

        activity.setCommentCount((activity.getCommentCount() == null ? 0 : activity.getCommentCount()) + 1);
        activityRepository.save(activity);

        return Comment.from(saved);
    }

    @Transactional
    public void deleteComment(UUID commentId, UUID userId) {
        ActivityComment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> ApiException.notFound("comment"));
        boolean isAuthor = comment.getUser().getId().equals(userId);
        boolean isOwner = comment.getActivity().getUser().getId().equals(userId);
        if (!isAuthor && !isOwner) {
            throw ApiException.forbidden("Only the comment author or activity owner can delete");
        }
        Activity activity = comment.getActivity();
        commentRepository.delete(comment);
        activity.setCommentCount(Math.max(0, (activity.getCommentCount() == null ? 0 : activity.getCommentCount()) - 1));
        activityRepository.save(activity);
    }

    public Page<ActivityComment> listComments(UUID activityId, int page, int limit) {
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> ApiException.notFound("activity"));
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(limit, 100));
        return commentRepository.findByActivityOrderByCreatedAtAsc(activity, pageable);
    }
}
