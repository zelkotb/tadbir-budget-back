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
import ma.zakaria.tadbirbudget.entity.enums.PhaseStatus;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A phase (étape) of a {@link Project} — how the project is followed step by step. Each phase carries
 * a {@code weight} (its share of the project; the phases' weights sum to at most 100) and a
 * {@code completion} (its own progress, 0→100 over time). {@code firstStartDate}/{@code firstEndDate}
 * are the immutable baseline (set at creation) used to compute delays against the current schedule.
 * {@code @Audited} so every change is attributed to its actor.
 */
@Audited
@Entity
@Table(name = "project_phase")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectPhase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhaseStatus status;

    /** Share of the project (poids), 0–100. The project's phases' weights sum to at most 100. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    /** Progress of this phase (avancement), 0–100; increases over time toward 100. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal completion;

    /** Current planned schedule (editable). */
    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** Baseline schedule, set once at creation and never changed — used to compute delays. */
    @Column(name = "first_start_date")
    private LocalDate firstStartDate;

    @Column(name = "first_end_date")
    private LocalDate firstEndDate;

    /** uid of the creator. */
    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
