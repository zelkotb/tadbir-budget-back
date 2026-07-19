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
package ma.zakaria.tadbirbudget.entity;

import jakarta.persistence.*;
import lombok.*;
import ma.zakaria.tadbirbudget.entity.enums.AuthEventType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "`auth_audit`",
    indexes = {
        @Index(name = "idx_auth_audit_actor",       columnList = "actor"),
        @Index(name = "idx_auth_audit_ip_address",  columnList = "ip_address"),
        @Index(name = "idx_auth_audit_event_type",  columnList = "event_type"),
        @Index(name = "idx_auth_audit_occurred_at", columnList = "occurred_at")
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private AuthEventType eventType;

    @Column(nullable = false)
    private boolean success;

    /** Login identifier (uid) used in the auth attempt. Null only for TOKEN_REFRESH failures with an unresolvable token. */
    @Column(name = "actor", length = 255)
    private String actor;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}