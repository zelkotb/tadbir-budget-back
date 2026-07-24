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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ma.zakaria.tadbirbudget.entity.enums.ProjectStatus;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A project (a.k.a. program — the label is a company-wide setting, not a per-project field). Owned
 * by an {@link OrgUnit}, run by a chef de projet (a {@link User}, referenced by id), with a team
 * ({@link ProjectMember}). Its budget (lignes + amounts, per year) is a separate concern.
 * {@code @Audited} so every change is attributed to its actor via Envers.
 */
@Audited
@Entity
@Table(name = "project")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String objectifs;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;

    /** The chef de projet — {@link User} id (not uid). */
    @Column(name = "chef_projet_id", nullable = false, columnDefinition = "uuid")
    private UUID chefProjetId;

    /** The org unit the project belongs to. */
    @Column(name = "org_unit_id", nullable = false, columnDefinition = "uuid")
    private UUID orgUnitId;

    /** Set when the project is started (status → ACTIVE). Null while NOT_STARTED. */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** Set when the project is TERMINATED. */
    @Column(name = "termination_year")
    private Integer terminationYear;

    /** uid of the creator. */
    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
