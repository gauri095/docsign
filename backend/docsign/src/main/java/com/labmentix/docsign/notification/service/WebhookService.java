package com.labmentix.docsign.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmentix.docsign.document.entity.Document;
import com.labmentix.docsign.notification.entity.WebhookDelivery;
import com.labmentix.docsign.notification.entity.WebhookEndpoint;
import com.labmentix.docsign.notification.repository.WebhookDeliveryRepository;
import com.labmentix.docsign.notification.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Delivers webhook events to owner-registered endpoints.
 *
 * Security model:
 * - Each delivery includes X-DocSign-Signature: sha256=HMAC(payload, endpoint.secret)
 * - Receivers verify the signature before trusting the payload
 *
 * Reliability model:
 * - Async delivery from a dedicated thread pool
 * - Failed deliveries are scheduled for exponential-backoff retry
 * - Max 5 attempts: 1m, 5m, 30m, 2h, 8h after initial failure
 * - Delivery log is immutable (WebhookDelivery rows — new row per attempt)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private static final int     MAX_ATTEMPTS  = 5;
    private static final long[]  BACKOFF_MINS  = {1, 5, 30, 120, 480};
    private static final Duration HTTP_TIMEOUT  = Duration.ofSeconds(10);

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ObjectMapper              objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ═══════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════

    /**
     * Builds a payload and dispatches to all active endpoints for this owner.
     * Fire-and-forget — runs on the notification thread pool.
     */
    @Async("notificationExecutor")
    public void dispatchEvent(String eventType, Document document, Map<String, Object> eventData) {
        List<WebhookEndpoint> endpoints = endpointRepository.findByActiveTrue();

        if (endpoints.isEmpty()) return;

        String payloadJson = buildPayload(eventType, document, eventData);

        for (WebhookEndpoint endpoint : endpoints) {
            // Filter: skip if endpoint only listens to specific events
            if (!shouldDeliver(endpoint, eventType)) continue;

            deliverToEndpoint(endpoint, document, eventType, payloadJson, 1);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DELIVERY
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public void deliverToEndpoint(
            WebhookEndpoint endpoint,
            Document        document,
            String          eventType,
            String          payloadJson,
            int             attemptNumber
    ) {
        String signature = sign(payloadJson, endpoint.getSecret());
        String sigHeader = "sha256=" + signature;

        Integer httpStatus   = null;
        String  responseBody = null;
        boolean succeeded    = false;
        String  errorMsg     = null;
        Instant nextRetry    = null;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.getUrl()))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type",      "application/json")
                    .header("X-DocSign-Signature", sigHeader)
                    .header("X-DocSign-Event",     eventType)
                    .header("User-Agent",          "DocSign-Webhook/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            httpStatus   = response.statusCode();
            responseBody = response.body();
            succeeded    = httpStatus >= 200 && httpStatus < 300;

            if (succeeded) {
                log.info("Webhook delivered: {} → {} [{}]", eventType, endpoint.getUrl(), httpStatus);
            } else {
                log.warn("Webhook non-2xx: {} → {} [{}]", eventType, endpoint.getUrl(), httpStatus);
                nextRetry = scheduleRetry(attemptNumber);
            }

        } catch (Exception e) {
            errorMsg  = e.getMessage();
            nextRetry = scheduleRetry(attemptNumber);
            log.warn("Webhook error: {} → {}: {}", eventType, endpoint.getUrl(), e.getMessage());
        }

        // Always record — immutable delivery log
        deliveryRepository.save(WebhookDelivery.builder()
                .endpoint(endpoint)
                .document(document)
                .eventType(eventType)
                .payloadJson(payloadJson)
                .signatureHeader(sigHeader)
                .httpStatus(httpStatus)
                .responseBody(responseBody != null && responseBody.length() > 2000
                        ? responseBody.substring(0, 2000) + "…" : responseBody)
                .attemptNumber(attemptNumber)
                .succeeded(succeeded)
                .errorMessage(errorMsg)
                .nextRetryAt(nextRetry)
                .build());
    }

    // ═══════════════════════════════════════════════════════════
    // RETRY SCHEDULER
    // ═══════════════════════════════════════════════════════════

    /**
     * Runs every minute. Picks up failed deliveries whose next_retry_at has passed.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void retryFailedDeliveries() {
        List<WebhookDelivery> pending = deliveryRepository.findPendingRetries(Instant.now());
        if (pending.isEmpty()) return;

        log.info("Webhook retry sweep: {} deliveries pending", pending.size());
        for (WebhookDelivery delivery : pending) {
            int nextAttempt = delivery.getAttemptNumber() + 1;
            if (nextAttempt > MAX_ATTEMPTS) {
                log.warn("Webhook max attempts reached for delivery {} — giving up", delivery.getId());
                continue;
            }
            deliverToEndpoint(
                    delivery.getEndpoint(),
                    delivery.getDocument(),
                    delivery.getEventType(),
                    delivery.getPayloadJson(),
                    nextAttempt
            );
        }
    }

    // ═══════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════

    private String buildPayload(String eventType, Document document, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event",      eventType);
        payload.put("timestamp",  Instant.now().toString());
        payload.put("documentId", document != null ? document.getId().toString() : null);
        payload.put("data",       data);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Webhook payload serialisation failed", e);
            return "{}";
        }
    }

    /**
     * Signs the payload with HMAC-SHA256 using the endpoint's private secret.
     * Receivers verify: sha256=HMAC(secret, payload_body)
     */
    private String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Webhook signature failed", e);
        }
    }

    private boolean shouldDeliver(WebhookEndpoint endpoint, String eventType) {
        String[] filter = endpoint.getEventFilter();
        if (filter == null || filter.length == 0) return true;
        return Arrays.asList(filter).contains(eventType);
    }

    private Instant scheduleRetry(int attemptNumber) {
        if (attemptNumber >= MAX_ATTEMPTS) return null;
        long delayMinutes = BACKOFF_MINS[Math.min(attemptNumber - 1, BACKOFF_MINS.length - 1)];
        return Instant.now().plusSeconds(delayMinutes * 60);
    }
}