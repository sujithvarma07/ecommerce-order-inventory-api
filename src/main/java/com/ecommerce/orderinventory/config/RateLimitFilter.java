package com.ecommerce.orderinventory.config;

import com.ecommerce.orderinventory.exception.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory, fixed-window rate limiter applied to /api/v1/** — one window (default 60s)
 * per client IP, with a configurable request cap (app.rate-limit.requests-per-minute). This is a
 * single-instance solution (the counters live in this process's memory); a multi-instance
 * deployment would move the counters to a shared store (e.g. Redis) behind the same filter.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;

    private final ObjectMapper objectMapper;

    @Value("${app.rate-limit.requests-per-minute}")
    private int requestsPerMinute;

    private final ConcurrentHashMap<String, Window> windowsByClient = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String clientKey = clientKey(request);
        Window window = windowsByClient.computeIfAbsent(clientKey, key -> new Window());

        if (window.tryConsume(requestsPerMinute)) {
            filterChain.doFilter(request, response);
            return;
        }

        ApiErrorResponse error = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .message("Rate limit exceeded (" + requestsPerMinute + " requests/minute). Please slow down and retry shortly.")
                .path(request.getRequestURI())
                .build();

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * A fixed window counter: resets itself once WINDOW_MILLIS has elapsed since it started.
     */
    private static final class Window {
        private volatile long windowStart = System.currentTimeMillis();
        private int count = 0;

        synchronized boolean tryConsume(int limit) {
            long now = System.currentTimeMillis();
            if (now - windowStart >= WINDOW_MILLIS) {
                windowStart = now;
                count = 0;
            }

            if (count >= limit) {
                return false;
            }

            count++;
            return true;
        }
    }
}
