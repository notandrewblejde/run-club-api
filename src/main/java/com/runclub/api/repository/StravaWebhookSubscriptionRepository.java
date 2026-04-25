package com.runclub.api.repository;

import com.runclub.api.entity.StravaWebhookSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StravaWebhookSubscriptionRepository extends JpaRepository<StravaWebhookSubscription, Integer> {
}
