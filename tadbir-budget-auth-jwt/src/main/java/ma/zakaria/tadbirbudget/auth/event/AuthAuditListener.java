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
package ma.zakaria.tadbirbudget.auth.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.entity.AuthAudit;
import ma.zakaria.tadbirbudget.repository.AuthAuditRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Persists {@link AuthAuditEvent}s after the originating transaction commits.
 * Runs asynchronously so it never blocks the auth response.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthAuditListener {

    private final AuthAuditRepository authAuditRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(AuthAuditEvent event) {
        try {
            authAuditRepository.save(AuthAudit.builder()
                    .email(event.getEmail())
                    .eventType(event.getEventType())
                    .success(event.isSuccess())
                    .ipAddress(event.getIpAddress())
                    .userAgent(event.getUserAgent())
                    .occurredAt(event.getOccurredAt())
                    .build());
        } catch (Exception ex) {
            log.error("Failed to persist auth audit event type={} email={} success={}",
                    event.getEventType(), event.getEmail(), event.isSuccess(), ex);
        }
    }
}