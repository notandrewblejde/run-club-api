package com.runclub.api.repository;

import com.runclub.api.entity.Activity;
import com.runclub.api.entity.ActivityComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ActivityCommentRepository extends JpaRepository<ActivityComment, UUID> {
    Page<ActivityComment> findByActivityOrderByCreatedAtAsc(Activity activity, Pageable pageable);
    long countByActivity(Activity activity);
}
