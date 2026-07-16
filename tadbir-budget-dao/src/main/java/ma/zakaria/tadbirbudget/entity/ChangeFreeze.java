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
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.util.UUID;

/**
 * Global "change freeze" switch, toggled by an admin. Single row (see {@link #SINGLETON_ID}).
 * When {@code frozen = true}, ROLE_USER citizens may not sign up and may not create or edit
 * their requests. Admins are unaffected. {@code @Audited} records who toggled it and when.
 */
@Audited
@Entity
@Table(name = "change_freeze")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeFreeze {

    /** The one and only row. */
    public static final UUID SINGLETON_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    @Builder.Default
    private boolean frozen = false;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", columnDefinition = "uuid")
    private UUID updatedBy;
}
