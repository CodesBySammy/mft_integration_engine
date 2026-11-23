package com.mft.integration.api.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mft.integration.api.dto.ErrorResponse;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Enterprise rate-limiting filter for /api/** endpoints using an in-memory sliding-window counter.
 * Protects the engine against Denial of Service (DoS) attacks and aggressive API hammering.
 */
@Component
@Order(1)
public class ApiRateLimitingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ApiRateLimitingFilter.class);

    private static final int MAX_REQUESTS_PER_MINUTE = 120;
    private static final long WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, Deque<Long>> clientRequestTimestamps = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String uri = request.getRequestURI();

        // Only rate limit /api endpoints (exclude static frontend files, html, css, js)
        if (!uri.startsWith("/api")) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        String clientIp = resolveClientIp(request);
        long now = System.currentTimeMillis();

        Deque<Long> timestamps = clientRequestTimestamps.computeIfAbsent(clientIp, k -> new ConcurrentLinkedDeque<>());

        // Evict timestamps older than 60s sliding window
        while (!timestamps.isEmpty() && timestamps.peekFirst() < (now - WINDOW_MS)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
            log.warn("Rate limit exceeded for client IP [{}] on [{}] ({} reqs in last 60s)", clientIp, uri, timestamps.size());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ErrorResponse error = new ErrorResponse(
                    "RATE_LIMIT_EXCEEDED",
                    "Too many requests. Rate limit of " + MAX_REQUESTS_PER_MINUTE + " requests per minute exceeded.",
                    uri
            );
            response.getWriter().write(objectMapper.writeValueAsString(error));
            return;
        }

        timestamps.addLast(now);
        filterChain.doFilter(servletRequest, servletResponse);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
