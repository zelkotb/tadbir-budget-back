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
package ma.zakaria.tadbirbudget.audit;

import ma.zakaria.tadbirbudget.entity.RevInfo;
import org.hibernate.envers.RevisionListener;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Populates {@link RevInfo} with the authenticated user's login identifier and client IP
 * on every Envers revision.
 *
 * The actor is read from Spring Security's {@code SecurityContextHolder}
 * (User.getUsername() returns the {@code uid} — the unique login identifier).
 * IP is read from SLF4J MDC (populated per-request by {@code MdcFilter}).
 */
public class CustomRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        RevInfo info = (RevInfo) revisionEntity;
        info.setActor(resolveActor());
        info.setIp(MDC.get("ip"));
    }

    private String resolveActor() {
        // Authenticated operations (admin actions, updates, etc.)
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
        } catch (Exception ignored) {
        }
        return "system";
    }
}