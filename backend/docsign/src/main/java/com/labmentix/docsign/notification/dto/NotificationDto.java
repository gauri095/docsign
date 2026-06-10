package com.labmentix.docsign.notification.dto;

import com.labmentix.docsign.notification.entity.InAppNotification;
import com.labmentix.docsign.notification.entity.WebhookDelivery;
import com.labmentix.docsign.notification.entity.WebhookEndpoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class NotificationDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationResponse {
        private UUID id;
        private String message;
        private String type;
        private boolean read;
        private LocalDateTime createdAt;

        public static NotificationResponse from(InAppNotification notification) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getMessage(),
                    notification.getType(),
                    notification.isRead(),
                    notification.getCreatedAt()
            );
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationPageResponse {
        private List<NotificationResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean last;
        private long unreadCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnreadCountResponse {
        private long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateWebhookRequest {
        private String url;
        private String description;
        private List<String> eventFilter;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WebhookEndpointResponse {
        private UUID id;
        private String url;
        private String description;
        private String secret;
        private boolean active;
        private List<String> eventFilter;
        private LocalDateTime createdAt;

        public static WebhookEndpointResponse from(WebhookEndpoint endpoint, String secret) {
            return new WebhookEndpointResponse(
                    endpoint.getId(),
                    endpoint.getUrl(),
                    endpoint.getDescription(),
                    secret,
                    endpoint.isActive(),
                    endpoint.getEventFilter() != null ? List.of(endpoint.getEventFilter()) : null,
                    endpoint.getCreatedAt()
            );
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WebhookDeliveryResponse {
        private UUID id;
        private UUID endpointId;
        private String payload;
        private int statusCode;
        private String response;
        private LocalDateTime deliveredAt;

        public static WebhookDeliveryResponse from(WebhookDelivery delivery) {
            return new WebhookDeliveryResponse(
                    delivery.getId(),
                    delivery.getEndpoint().getId(),
                    delivery.getPayload(),
                    delivery.getStatusCode(),
                    delivery.getResponse(),
                    delivery.getDeliveredAt()
            );
        }
    }
}
