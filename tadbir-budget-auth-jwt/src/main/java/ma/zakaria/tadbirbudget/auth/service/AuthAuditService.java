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
package ma.zakaria.tadbirbudget.auth.service;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.auth.dto.AuthAuditResponse;
import ma.zakaria.tadbirbudget.auth.dto.AuthRequestContext;
import ma.zakaria.tadbirbudget.auth.event.AuthAuditEvent;
import ma.zakaria.tadbirbudget.entity.AuthAudit;
import ma.zakaria.tadbirbudget.entity.enums.AuthEventType;
import ma.zakaria.tadbirbudget.repository.AuthAuditRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthAuditService {

    private final ApplicationEventPublisher eventPublisher;
    private final AuthAuditRepository       authAuditRepository;

    /**
     * Publishes a successful auth event.
     * Persistence happens asynchronously after the caller's transaction commits,
     * so this never blocks the auth response.
     */
    public void recordSuccess(String email, AuthEventType eventType, AuthRequestContext ctx) {
        eventPublisher.publishEvent(
                new AuthAuditEvent(email, eventType, true, ctx.ipAddress(), ctx.userAgent()));
    }

    /**
     * Persists a failed auth event in its own independent transaction.
     * Must run in {@code REQUIRES_NEW} because the caller's transaction is
     * about to roll back (exception in flight) and the audit row must survive.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String email, AuthEventType eventType, AuthRequestContext ctx) {
        try {
            authAuditRepository.save(AuthAudit.builder()
                    .email(email)
                    .eventType(eventType)
                    .success(false)
                    .ipAddress(ctx.ipAddress())
                    .userAgent(ctx.userAgent())
                    .occurredAt(Instant.now())
                    .build());
        } catch (Exception ex) {
            log.error("Failed to persist failed auth audit event type={} email={}", eventType, email, ex);
        }
    }

    /**
     * Returns a paginated, filterable view of the auth audit log.
     * {@code email} and {@code ipAddress} support partial LIKE matching.
     * {@code date} matches against {@code occurred_at} formatted as {@code DD/MM/YYYY},
     * so partial inputs like {@code "04/06"} or {@code "04/06/2026"} all work.
     */
    @Transactional(readOnly = true)
    public Page<AuthAuditResponse> query(String email, String ipAddress,
                                         AuthEventType eventType, Boolean success,
                                         String date, Pageable pageable) {
        Specification<AuthAudit> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (email     != null) predicates.add(cb.like(cb.lower(root.get("email")),
                                                          "%" + email.toLowerCase() + "%"));
            if (ipAddress != null) predicates.add(cb.like(root.get("ipAddress"),
                                                          "%" + ipAddress + "%"));
            if (eventType != null) predicates.add(cb.equal(root.get("eventType"), eventType));
            if (success   != null) predicates.add(cb.equal(root.get("success"),   success));
            if (date      != null) {
                Expression<String> formatted = cb.function(
                        "TO_CHAR", String.class,
                        root.get("occurredAt"), cb.literal("DD/MM/YYYY"));
                predicates.add(cb.like(formatted, date + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return authAuditRepository.findAll(spec, pageable).map(AuthAuditResponse::from);
    }
}