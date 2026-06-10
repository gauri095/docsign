package com.labmentix.docsign.notification.repository;

import com.labmentix.docsign.notification.entity.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findByEndpointIdOrderByDeliveredAtDesc(UUID endpointId);

    @Query("SELECT d FROM WebhookDelivery d WHERE d.succeeded = false AND d.nextRetryAt IS NOT NULL AND d.nextRetryAt <= :now")
    List<WebhookDelivery> findPendingRetries(Instant now);
}
