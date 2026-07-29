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
package ma.zakaria.tadbirbudget.project.dto;

import ma.zakaria.tadbirbudget.entity.enums.PhaseStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * <b>Phase-level</b> KPIs — one {@link PhaseKpi} per phase of the project, computed on the backend at
 * {@code referenceDate}. Complements the project-level {@link ProjectKpiResponse}. Percentages are
 * 0–100 (2 decimals); delays/durations are in days ({@code > 0} = late / stretched).
 */
public record ProjectPhaseKpiResponse(
        UUID           projectId,
        LocalDate      referenceDate,
        List<PhaseKpi> phases
) {
    public record PhaseKpi(
            UUID           phaseId,
            UUID           parentPhaseId,        // null for a top-level phase
            String         title,
            PhaseStatus    status,

            double         weight,               // poids
            double         completion,           // avancement réel (rolled up from sous-phases for a parent)
            double         completionPlanifiee,  // expected progress at referenceDate
            double         ecartAvancement,      // completion − completionPlanifiee
            double         contributionPonderee, // weight·completion / 100 (0 when cancelled)
            ScheduleHealth statutDelai,          // derived badge from ecartAvancement

            Long           retardJours,          // endDate − firstEndDate (null if a date is missing)
            Long           retardDebutJours,     // startDate − firstStartDate

            Long           dureePlanifieeJours,  // firstEndDate − firstStartDate (baseline)
            Long           dureeEstimeeJours,    // endDate − startDate (current)
            Long           glissementJours,      // dureeEstimee − dureePlanifiee (stretch)

            LocalDate      firstStartDate,
            LocalDate      firstEndDate,
            LocalDate      startDate,
            LocalDate      endDate,

            List<PhaseKpi> sousPhases            // sous-phase KPIs (empty for a sous-phase / leaf)
    ) {}
}
