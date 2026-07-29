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

import java.time.LocalDate;
import java.util.UUID;

/**
 * <b>Project-level</b> KPIs, computed on the backend from the project's phases — a lean, non-redundant
 * headline set (per-phase detail lives in {@link ProjectPhaseKpiResponse}).
 *
 * <p>Both advancements are measured against the <b>whole</b> project (0–100):
 * <ul>
 *   <li>{@code avancementPlanifie} = Σ weight — how much of the project has been planned into phases;
 *       climbs toward 100 as phases are added (the target ceiling).</li>
 *   <li>{@code avancementPondere} = Σ(weight·completion) / 100 — actual progress; unplanned weight
 *       counts as 0. Always {@code ≤ avancementPlanifie ≤ 100}.</li>
 * </ul>
 * Example: one phase, weight 10, completion 50 → planifié = 10, pondéré = 5.
 *
 * <p>Trivial diffs are left to the front: reste à faire = 100 − avancementPondere; planning gap =
 * 100 − avancementPlanifie; global delay in days = dateFinEstimee − dateFinReference.
 */
public record ProjectKpiResponse(
        UUID      projectId,
        LocalDate referenceDate,

        // Top-level phase counts by status (total = their sum)
        int       countCreated,
        int       countActive,
        int       countTerminated,
        int       countCancelled,

        // Advancement against the whole project
        double    avancementPlanifie,   // Σ weight (planned coverage / target)
        double    avancementPondere,    // Σ(weight·completion) / 100 (actual)

        // Delay
        int       phasesEnRetard,       // # phases whose endDate is past their firstEndDate

        // Finish — baseline vs current estimate (delay in days = estimee − reference)
        LocalDate dateFinReference,     // max firstEndDate
        LocalDate dateFinEstimee        // max endDate
) {
    /** KPIs for a project with no phases yet. */
    public static ProjectKpiResponse empty(UUID projectId, LocalDate referenceDate) {
        return new ProjectKpiResponse(projectId, referenceDate,
                0, 0, 0, 0,
                0d, 0d,
                0,
                null, null);
    }
}
