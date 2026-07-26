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
package ma.zakaria.tadbirbudget.project.service;

import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.entity.ProjectPhase;
import ma.zakaria.tadbirbudget.project.dto.ProjectKpiResponse;
import ma.zakaria.tadbirbudget.project.dto.ProjectPhaseKpiResponse;
import ma.zakaria.tadbirbudget.project.dto.ProjectPhaseKpiResponse.PhaseKpi;
import ma.zakaria.tadbirbudget.project.dto.ScheduleHealth;
import ma.zakaria.tadbirbudget.repository.ProjectPhaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Computes the two families of KPIs from a project's phases — the single source of truth (the front
 * just renders): <b>project-level</b> rollups ({@link #projectKpis}) and <b>phase-level</b> figures
 * ({@link #phaseKpis}). Advancement is phase completion weighted by poids; planned advancement and
 * delays come from the current schedule vs the immutable baseline (first*), evaluated at "today".
 */
@Service
@RequiredArgsConstructor
public class ProjectKpiService {

    /** Tolerance (percentage points) before a project/phase is flagged ahead or behind plan. */
    private static final double HEALTH_TOLERANCE = 5.0;

    private final ProjectService         projectService;
    private final ProjectPhaseRepository phaseRepository;

    // ── Project-level KPIs ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProjectKpiResponse projectKpis(UUID projectId) {
        projectService.requireReadable(projectId);
        List<ProjectPhase> phases = phaseRepository.findByProjectIdOrderByStartDateAscCreatedAtAsc(projectId);
        LocalDate today = LocalDate.now();

        if (phases.isEmpty()) {
            return ProjectKpiResponse.empty(projectId, today);
        }

        double sumW = 0, sumWC = 0;
        int created = 0, active = 0, terminated = 0, enRetard = 0;
        LocalDate maxFe = null, maxE = null;

        for (ProjectPhase p : phases) {
            double w = dbl(p.getWeight());
            double c = dbl(p.getCompletion());

            sumW  += w;              // planned coverage (Σ weight)
            sumWC += w * c;          // weighted progress numerator (Σ weight·completion)

            switch (p.getStatus()) {
                case CREATED    -> created++;
                case ACTIVE     -> active++;
                case TERMINATED -> terminated++;
            }
            Long retard = daysBetween(p.getFirstEndDate(), p.getEndDate());
            if (retard != null && retard > 0) {
                enRetard++;
            }
            maxFe = latest(maxFe, p.getFirstEndDate());
            maxE  = latest(maxE,  p.getEndDate());
        }

        // Both against the whole project (100): planifié = Σweight, pondéré = Σ(weight·completion)/100.
        double avancementPlanifie = round2(sumW);
        double avancementPondere  = round2(sumWC / 100.0);

        return new ProjectKpiResponse(projectId, today,
                created, active, terminated,
                avancementPlanifie, avancementPondere,
                enRetard,
                maxFe, maxE);
    }

    // ── Phase-level KPIs ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProjectPhaseKpiResponse phaseKpis(UUID projectId) {
        projectService.requireReadable(projectId);
        List<ProjectPhase> phases = phaseRepository.findByProjectIdOrderByStartDateAscCreatedAtAsc(projectId);
        LocalDate today = LocalDate.now();
        List<PhaseKpi> items = phases.stream().map(p -> phaseKpi(p, today)).toList();
        return new ProjectPhaseKpiResponse(projectId, today, items);
    }

    private PhaseKpi phaseKpi(ProjectPhase p, LocalDate today) {
        double w = dbl(p.getWeight());
        double c = dbl(p.getCompletion());
        double planned = plannedCompletion(p.getFirstStartDate(), p.getFirstEndDate(), today);

        Long retard      = daysBetween(p.getFirstEndDate(),   p.getEndDate());
        Long retardDebut = daysBetween(p.getFirstStartDate(), p.getStartDate());
        Long dureePlan   = daysBetween(p.getFirstStartDate(), p.getFirstEndDate());
        Long dureeEst    = daysBetween(p.getStartDate(),      p.getEndDate());
        Long glissement  = (dureePlan != null && dureeEst != null) ? dureeEst - dureePlan : null;

        return new PhaseKpi(p.getId(), p.getTitle(), p.getStatus(),
                round2(w), round2(c), round2(planned), round2(c - planned),
                round2(w * c / 100.0), health(c, planned),
                retard, retardDebut,
                dureePlan, dureeEst, glissement,
                p.getFirstStartDate(), p.getFirstEndDate(), p.getStartDate(), p.getEndDate());
    }

    // ── Shared computation ──────────────────────────────────────────────────────

    /** Expected progress (0–100) of a phase at {@code today}, linear over its baseline schedule. */
    private double plannedCompletion(LocalDate firstStart, LocalDate firstEnd, LocalDate today) {
        if (firstStart == null || firstEnd == null) {
            return 0;
        }
        if (!today.isAfter(firstStart)) {
            return 0;                       // not started yet
        }
        if (!today.isBefore(firstEnd)) {
            return 100;                     // planned to be done
        }
        long total = firstEnd.toEpochDay() - firstStart.toEpochDay();
        if (total <= 0) {
            return 100;
        }
        return 100.0 * (today.toEpochDay() - firstStart.toEpochDay()) / total;
    }

    /** Derived schedule health from the gap between actual and planned advancement. */
    private ScheduleHealth health(double actual, double planned) {
        if (planned <= 0) {
            return actual > 0 ? ScheduleHealth.EN_AVANCE : ScheduleHealth.INDETERMINE;
        }
        double gap = actual - planned;
        if (gap >= HEALTH_TOLERANCE) {
            return ScheduleHealth.EN_AVANCE;
        }
        if (gap <= -HEALTH_TOLERANCE) {
            return ScheduleHealth.EN_RETARD;
        }
        return ScheduleHealth.DANS_LES_TEMPS;
    }

    private Long daysBetween(LocalDate from, LocalDate to) {
        return (from == null || to == null) ? null : to.toEpochDay() - from.toEpochDay();
    }

    private LocalDate latest(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return b.isAfter(a) ? b : a;
    }

    private double dbl(BigDecimal v) {
        return v != null ? v.doubleValue() : 0d;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
