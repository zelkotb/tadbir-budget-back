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
import ma.zakaria.tadbirbudget.entity.enums.PhaseStatus;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes the two KPI families from a project's phases and sous-phases — the single source of truth.
 * A parent phase's completion is the (denormalized) roll-up of its non-cancelled sous-phases; project
 * advancement then rolls those top-level phases up again. Cancelled phases/sous-phases are excluded
 * from weights and advancement. Planned advancement per phase and delays come from the current
 * schedule vs the immutable baseline (first*), evaluated at "today".
 */
@Service
@RequiredArgsConstructor
public class ProjectKpiService {

    private static final double HEALTH_TOLERANCE = 5.0;

    private final ProjectService         projectService;
    private final ProjectPhaseRepository phaseRepository;

    // ── Project-level KPIs ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProjectKpiResponse projectKpis(UUID projectId) {
        projectService.requireReadable(projectId);
        LocalDate today = LocalDate.now();

        List<ProjectPhase> topLevel = phaseRepository
                .findByProjectIdAndParentPhaseIdIsNullOrderByStartDateAscCreatedAtAsc(projectId);
        if (topLevel.isEmpty()) {
            return ProjectKpiResponse.empty(projectId, today);
        }

        double sumW = 0, sumWC = 0;
        int created = 0, active = 0, terminated = 0, cancelled = 0, enRetard = 0;
        LocalDate maxFe = null, maxE = null;

        for (ProjectPhase p : topLevel) {
            switch (p.getStatus()) {
                case CREATED    -> created++;
                case ACTIVE     -> active++;
                case TERMINATED -> terminated++;
                case CANCELLED  -> cancelled++;
            }
            if (p.getStatus() == PhaseStatus.CANCELLED) {
                continue;   // cancelled phases do not count toward weights / advancement / delay
            }
            sumW  += dbl(p.getWeight());
            sumWC += dbl(p.getWeight()) * dbl(p.getCompletion());   // completion already rolled up

            Long retard = daysBetween(p.getFirstEndDate(), p.getEndDate());
            if (retard != null && retard > 0) {
                enRetard++;
            }
            maxFe = latest(maxFe, p.getFirstEndDate());
            maxE  = latest(maxE,  p.getEndDate());
        }

        double avancementPlanifie = round2(sumW);
        double avancementPondere  = round2(sumWC / 100.0);

        return new ProjectKpiResponse(projectId, today,
                created, active, terminated, cancelled,
                avancementPlanifie, avancementPondere,
                enRetard,
                maxFe, maxE);
    }

    // ── Phase-level KPIs ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProjectPhaseKpiResponse phaseKpis(UUID projectId) {
        projectService.requireReadable(projectId);
        LocalDate today = LocalDate.now();

        List<ProjectPhase> all = phaseRepository.findByProjectIdOrderByStartDateAscCreatedAtAsc(projectId);
        Map<UUID, List<ProjectPhase>> byParent = all.stream()
                .filter(p -> p.getParentPhaseId() != null)
                .collect(Collectors.groupingBy(ProjectPhase::getParentPhaseId));

        List<PhaseKpi> phases = all.stream()
                .filter(p -> p.getParentPhaseId() == null)
                .map(p -> phaseKpi(p, byParent.getOrDefault(p.getId(), List.of()), today))
                .toList();

        return new ProjectPhaseKpiResponse(projectId, today, phases);
    }

    private PhaseKpi phaseKpi(ProjectPhase p, List<ProjectPhase> subs, LocalDate today) {
        double w = dbl(p.getWeight());
        double c = dbl(p.getCompletion());
        double planned = plannedCompletion(p.getFirstStartDate(), p.getFirstEndDate(), today);
        boolean cancelled = p.getStatus() == PhaseStatus.CANCELLED;

        Long retard      = daysBetween(p.getFirstEndDate(),   p.getEndDate());
        Long retardDebut = daysBetween(p.getFirstStartDate(), p.getStartDate());
        Long dureePlan   = daysBetween(p.getFirstStartDate(), p.getFirstEndDate());
        Long dureeEst    = daysBetween(p.getStartDate(),      p.getEndDate());
        Long glissement  = (dureePlan != null && dureeEst != null) ? dureeEst - dureePlan : null;

        List<PhaseKpi> sousKpis = subs.stream()
                .map(s -> phaseKpi(s, List.of(), today))
                .toList();

        return new PhaseKpi(p.getId(), p.getParentPhaseId(), p.getTitle(), p.getStatus(),
                round2(w), round2(c), round2(planned), round2(c - planned),
                cancelled ? 0d : round2(w * c / 100.0), health(c, planned),
                retard, retardDebut,
                dureePlan, dureeEst, glissement,
                p.getFirstStartDate(), p.getFirstEndDate(), p.getStartDate(), p.getEndDate(),
                sousKpis);
    }

    // ── Shared computation ──────────────────────────────────────────────────────

    /** Expected progress (0–100) of a phase at {@code today}, linear over its baseline schedule. */
    private double plannedCompletion(LocalDate firstStart, LocalDate firstEnd, LocalDate today) {
        if (firstStart == null || firstEnd == null) {
            return 0;
        }
        if (!today.isAfter(firstStart)) {
            return 0;
        }
        if (!today.isBefore(firstEnd)) {
            return 100;
        }
        long total = firstEnd.toEpochDay() - firstStart.toEpochDay();
        if (total <= 0) {
            return 100;
        }
        return 100.0 * (today.toEpochDay() - firstStart.toEpochDay()) / total;
    }

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
