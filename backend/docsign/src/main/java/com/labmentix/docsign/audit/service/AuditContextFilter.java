package com.labmentix.docsign.audit.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs early in the filter chain — captures IP and User-Agent into
 * the request-scoped {@link AuditContext} bean before any service logic runs.
 *
 * Ordering: runs after Spring Security (order 10) but before business logic.
 */
@Component
@Order(20)
@RequiredArgsConstructor
public class AuditContextFilter extends OncePerRequestFilter {

    private final AuditContext auditContext;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        auditContext.populate(request);
        filterChain.doFilter(request, response);
    }
}