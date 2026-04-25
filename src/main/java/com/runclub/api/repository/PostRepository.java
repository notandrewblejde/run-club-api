package com.runclub.api.repository;

import com.runclub.api.entity.Club;
import com.runclub.api.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {
    Page<Post> findByClubOrderByCreatedAtDesc(Club club, Pageable pageable);
}
