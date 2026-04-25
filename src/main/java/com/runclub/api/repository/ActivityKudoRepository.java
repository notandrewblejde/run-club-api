package com.runclub.api.repository;

import com.runclub.api.entity.Activity;
import com.runclub.api.entity.ActivityKudo;
import com.runclub.api.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActivityKudoRepository extends JpaRepository<ActivityKudo, UUID> {
    Optional<ActivityKudo> findByActivityAndUser(Activity activity, User user);
    Page<ActivityKudo> findByActivity(Activity activity, Pageable pageable);
    long countByActivity(Activity activity);
    void deleteByActivityAndUser(Activity activity, User user);
}
