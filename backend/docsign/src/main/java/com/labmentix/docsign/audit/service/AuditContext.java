package com.labmentix.docsign.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped bean that captures HTTP context (IP, User-Agent)
 * once per request and makes it available to the AuditAspect
 * without passing HttpServletRequest through every service call.
 *
 * Populated by {@link AuditContextFilter} on every inbound HTTP request.
 */
@Component
@RequestScope
@Getter
@RequiredArgsConstructor
public class AuditContext {

    private String ipAddress;
    private String userAgent;

    public void populate(HttpServletRequest request) {
        this.ipAddress = extractIp(request);
        this.userAgent = request.getHeader("User-Agent");
    }

    private static String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}