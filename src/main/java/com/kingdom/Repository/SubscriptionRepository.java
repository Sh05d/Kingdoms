package com.kingdom.Repository;

import com.kingdom.Enums.SubscriptionStatus;
import com.kingdom.Model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Integer> {

    Subscription findSubscriptionById(Integer id);
    long countByStatus(SubscriptionStatus status);

    List<Subscription> findByStatusAndExpiresAtBetween(SubscriptionStatus status, LocalDateTime start, LocalDateTime end);

}
