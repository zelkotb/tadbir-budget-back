/*
 * Copyright (c) 2026 Zakaria El Kotb. All rights reserved.
 *
 * This source code is the exclusive property of Zakaria El Kotb.
 * Unauthorized copying, modification, distribution, or use of this file,
 * via any medium, is strictly prohibited without the prior written
 * permission of the copyright owner.
 *
 * Author: Zakaria El Kotb <elkotbzakaria@gmail.com>
 */
package ma.zakaria.tadbirbudget.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.exception.ApiError;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.ratelimit.RateLimitProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Enforces per-IP rate limits before any Spring Security processing.
 *
 * Runs at order -200 (before Spring Security at -100 and MdcFilter at 1).
 * Uses two separate Caffeine caches — one for auth endpoints, one for the
 * general API — each backed by a Bucket4j token-bucket per client IP.
 *
 * Responds with HTTP 429 + Retry-After header + ApiError JSON body when
 * a bucket is exhausted.  Informs clients of remaining quota via
 * X-RateLimit-Limit / X-RateLimit-Remaining on every response.
 *
 * Rate limits are configured under rate-limit.auth.* and rate-limit.api.*
 * and can be disabled entirely with rate-limit.enabled=false (dev profile).
 */
@Slf4j
@Component
@Order(-200)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";
    private static final String HEADER_LIMIT     = "X-RateLimit-Limit";
    private static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    private static final String HEADER_RETRY     = "Retry-After";

    private static final List<String> PROXY_HEADERS =
            List.of("X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP");

    private final RateLimitProperties  properties;
    private final ObjectMapper         objectMapper;
    private final CorsConfigurationSource corsConfigurationSource;

    private final Cache<String, Bucket> authBuckets = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    private final Cache<String, Bucket> apiBuckets = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Let preflight requests pass through — CORS handles them, and they must not consume tokens.
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
            throws ServletException, IOException {

        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String  ip     = resolveClientIp(request);
        String  path   = request.getRequestURI();
        boolean isAuth = path.startsWith(AUTH_PATH_PREFIX);

        RateLimitProperties.BucketConfig config = isAuth ? properties.getAuth() : properties.getApi();
        Cache<String, Bucket>            cache  = isAuth ? authBuckets : apiBuckets;

        Bucket           bucket = cache.get(ip, k -> buildBucket(config));
        ConsumptionProbe probe  = bucket.tryConsumeAndReturnRemaining(1);

        response.setHeader(HEADER_LIMIT, String.valueOf(config.getCapacity()));

        if (probe.isConsumed()) {
            response.setHeader(HEADER_REMAINING, String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
        } else {
            long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
            log.warn("Rate limit exceeded ip=[{}] path=[{}] retryAfter=[{}s]", ip, path, retryAfterSeconds);

            applyCorsHeaders(request, response);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader(HEADER_RETRY,     String.valueOf(retryAfterSeconds));
            response.setHeader(HEADER_REMAINING, "0");
            response.getWriter().write(
                    objectMapper.writeValueAsString(ApiError.of(ErrorCode.RATE_LIMIT_EXCEEDED, 429)));
        }
    }

    /**
     * Copies CORS headers onto a short-circuit response (e.g. 429) so the browser
     * can read the error body. Without this, the browser sees a CORS failure instead
     * of the actual 429, making the error opaque to the frontend.
     */
    private void applyCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        CorsConfiguration config = corsConfigurationSource.getCorsConfiguration(request);
        if (config == null) return;
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null) return;
        String allowed = config.checkOrigin(origin);
        if (allowed == null) return;
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowed);
        if (Boolean.TRUE.equals(config.getAllowCredentials())) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
    }

    private Bucket buildBucket(RateLimitProperties.BucketConfig config) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(config.getCapacity())
                        .refillGreedy(config.getRefillTokens(),
                                Duration.ofSeconds(config.getRefillSeconds()))
                        .build())
                .build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        for (String header : PROXY_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
