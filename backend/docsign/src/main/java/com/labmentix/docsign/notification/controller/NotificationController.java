package com.labmentix.docsign.notification.controller;

import com.labmentix.docsign.auth.entity.User;
import com.labmentix.docsign.common.exception.ForbiddenException;
import com.labmentix.docsign.notification.dto.NotificationDto.*;
import com.labmentix.docsign.notification.entity.InAppNotification;
import com.labmentix.docsign.notification.entity.WebhookEndpoint;
import com.labmentix.docsign.notification.repository.WebhookDeliveryRepository;
import com.labmentix.docsign.notification.repository.WebhookEndpointRepository;
import com.labmentix.docsign.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService        notifService;
    private final WebhookEndpointRepository  endpointRepository;
    private final WebhookDeliveryRepository  deliveryRepository;

    // ── In-app notification feed ───────────────────────────────

    /**
     * GET /api/notifications?page=0&size=20
     */
    @GetMapping("/notifications")
    public ResponseEntity<NotificationPageResponse> getNotifications(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User currentUser
    ) {
        Page<InAppNotification> result =
                notifService.getNotificationsForUser(currentUser.getId(), page, size);

        return ResponseEntity.ok(new NotificationPageResponse(
                result.getContent().stream().map(NotificationResponse::from).toList(),
                result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast(),
                notifService.getUnreadCount(currentUser.getId())
        ));
    }

    /**
     * GET /api/notifications/unread-count
     */
    @GetMapping("/notifications/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(new UnreadCountResponse(
                notifService.getUnreadCount(currentUser.getId())
        ));
    }

    /**
     * PATCH /api/notifications/read-all
     */
    @PatchMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal User currentUser) {
        notifService.markAllRead(currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/notifications/{id}/read
     */
    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markOneRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        notifService.markOneRead(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    // ── Webhook endpoints ──────────────────────────────────────

    /**
     * POST /api/webhooks
     */
    @PostMapping("/webhooks")
    public ResponseEntity<WebhookEndpointResponse> createWebhook(
            @Valid @RequestBody CreateWebhookRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        String secret = UUID.randomUUID().toString().replace("-", "") +
                        UUID.randomUUID().toString().replace("-", "");

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setOwner(currentUser);
        endpoint.setUrl(request.url());
        endpoint.setSecret(secret);
        endpoint.setDescription(request.description());
        endpoint.setActive(true);
        endpoint.setEventFilter(request.eventFilter() != null
                ? request.eventFilter().toArray(String[]::new) : null);
        endpoint = endpointRepository.save(endpoint);

        // Return secret ONCE — never shown again
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WebhookEndpointResponse.from(endpoint, secret));
    }

    /**
     * GET /api/webhooks
     */
    @GetMapping("/webhooks")
    public ResponseEntity<List<WebhookEndpointResponse>> listWebhooks(
            @AuthenticationPrincipal User currentUser
    ) {
        List<WebhookEndpointResponse> endpoints = endpointRepository
                .findByOwnerIdAndActiveTrue(currentUser.getId())
                .stream()
                .map(e -> WebhookEndpointResponse.from(e, null))  // secret masked
                .toList();
        return ResponseEntity.ok(endpoints);
    }

    /**
     * DELETE /api/webhooks/{id}
     */
    @DeleteMapping("/webhooks/{id}")
    public ResponseEntity<Void> deleteWebhook(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        WebhookEndpoint endpoint = endpointRepository.findById(id)
                .orElseThrow(() -> new com.labmentix.docsign.common.exception.ResourceNotFoundException("Webhook", id));
        if (!endpoint.getOwner().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Not your webhook endpoint");
        }
        endpoint.setActive(false);
        endpointRepository.save(endpoint);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/webhooks/{id}/deliveries
     */
    @GetMapping("/webhooks/{id}/deliveries")
    public ResponseEntity<List<WebhookDeliveryResponse>> getDeliveries(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        WebhookEndpoint endpoint = endpointRepository.findById(id)
                .orElseThrow(() -> new com.labmentix.docsign.common.exception.ResourceNotFoundException("Webhook", id));
        if (!endpoint.getOwner().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Not your webhook endpoint");
        }

        List<WebhookDeliveryResponse> deliveries = deliveryRepository
                .findByEndpointIdOrderByDeliveredAtDesc(id)
                .stream()
                .map(WebhookDeliveryResponse::from)
                .toList();
        return ResponseEntity.ok(deliveries);
    }
}