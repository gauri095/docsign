package com.labmentix.docsign.notification.repository;

import com.labmentix.docsign.notification.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    List<WebhookEndpoint> findByOwnerIdAndActiveTrue(UUID ownerId);
    List<WebhookEndpoint> findByActiveTrue();
}
