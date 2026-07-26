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
import ma.zakaria.tadbirbudget.entity.Project;
import ma.zakaria.tadbirbudget.entity.ProjectPhase;
import ma.zakaria.tadbirbudget.entity.enums.PhaseStatus;
import ma.zakaria.tadbirbudget.entity.enums.ProjectStatus;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.project.dto.CreatePhaseInput;
import ma.zakaria.tadbirbudget.project.dto.PhaseResponse;
import ma.zakaria.tadbirbudget.project.dto.UpdatePhaseInput;
import ma.zakaria.tadbirbudget.repository.ProjectPhaseRepository;
import ma.zakaria.tadbirbudget.util.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Phases of a project (how it is followed step by step). Authorization is delegated to
 * {@link ProjectService}'s shared checkpoints — read for viewing, manage (chef or manager-in-scope)
 * for writes. A phase is created {@code CREATED}; its status moves forward only
 * ({@code CREATED → ACTIVE → TERMINATED}); a terminated phase can neither be edited nor transitioned.
 * The phases' weights (shares of the project) may never sum to more than 100. Every change is
 * Envers-audited to its actor.
 */
@Service
@RequiredArgsConstructor
public class ProjectPhaseService {

    private static final BigDecimal MAX_TOTAL_WEIGHT = new BigDecimal("100");
    private static final BigDecimal FULL_COMPLETION  = new BigDecimal("100");

    private final ProjectService         projectService;
    private final ProjectPhaseRepository phaseRepository;

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PhaseResponse> list(UUID projectId) {
        projectService.requireReadable(projectId);
        return phaseRepository.findByProjectIdOrderByStartDateAscCreatedAtAsc(projectId)
                .stream().map(PhaseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PhaseResponse get(UUID projectId, UUID phaseId) {
        projectService.requireReadable(projectId);
        return PhaseResponse.from(loadPhase(projectId, phaseId));
    }

    // ── Writes ──────────────────────────────────────────────────────────────────

    @Transactional
    public PhaseResponse create(UUID projectId, CreatePhaseInput in) {
        Project project = projectService.requireManageable(projectId);
        requireProjectAcceptsPhaseChanges(project);
        validateDates(in.getStartDate(), in.getEndDate());
        requireStartWithinProject(project, in.getStartDate());

        BigDecimal weight = nz(in.getWeight());
        requireWeightWithinBudget(projectId, null, weight);

        ProjectPhase phase = phaseRepository.save(ProjectPhase.builder()
                .projectId(projectId)
                .title(in.getTitle().trim())
                .description(trimToNull(in.getDescription()))
                .status(PhaseStatus.CREATED)
                .weight(weight)
                .completion(nz(in.getCompletion()))
                .startDate(in.getStartDate())
                .endDate(in.getEndDate())
                .firstStartDate(in.getStartDate())   // immutable baseline
                .firstEndDate(in.getEndDate())
                .createdBy(SecurityUtils.getCurrentUsername())
                .createdAt(Instant.now())
                .build());
        return PhaseResponse.from(phase);
    }

    @Transactional
    public PhaseResponse update(UUID projectId, UUID phaseId, UpdatePhaseInput in) {
        Project project = projectService.requireManageable(projectId);
        requireProjectAcceptsPhaseChanges(project);
        ProjectPhase phase = loadPhase(projectId, phaseId);
        requirePhaseNotTerminated(phase);

        if (in.getTitle() != null)       phase.setTitle(in.getTitle().trim());
        if (in.getDescription() != null) phase.setDescription(trimToNull(in.getDescription()));
        if (in.getStartDate() != null)   phase.setStartDate(in.getStartDate());
        if (in.getEndDate() != null)     phase.setEndDate(in.getEndDate());
        validateDates(phase.getStartDate(), phase.getEndDate());
        requireStartWithinProject(project, phase.getStartDate());
        if (in.getCompletion() != null)  phase.setCompletion(in.getCompletion());
        if (in.getWeight() != null) {
            requireWeightWithinBudget(projectId, phaseId, in.getWeight());
            phase.setWeight(in.getWeight());
        }
        return PhaseResponse.from(phaseRepository.save(phase));
    }

    /** Forward-only transition, one step at a time: CREATED → ACTIVE → TERMINATED. */
    @Transactional
    public PhaseResponse changeStatus(UUID projectId, UUID phaseId, PhaseStatus target) {
        Project project = projectService.requireManageable(projectId);
        requireProjectAcceptsPhaseChanges(project);
        ProjectPhase phase = loadPhase(projectId, phaseId);

        if (phase.getStatus() == PhaseStatus.TERMINATED
                || target.ordinal() != phase.getStatus().ordinal() + 1) {
            throw new CustomException(ErrorCode.PROJECT_PHASE_INVALID_STATUS, HttpStatus.CONFLICT);
        }
        // A phase can only be started once the project itself has started.
        if (target == PhaseStatus.ACTIVE && project.getStatus() != ProjectStatus.ACTIVE) {
            throw new CustomException(ErrorCode.PROJECT_NOT_ACTIVE, HttpStatus.CONFLICT);
        }
        phase.setStatus(target);
        if (target == PhaseStatus.TERMINATED) {
            phase.setCompletion(FULL_COMPLETION);   // a closed phase is 100% done
        }
        return PhaseResponse.from(phaseRepository.save(phase));
    }

    @Transactional
    public void delete(UUID projectId, UUID phaseId) {
        Project project = projectService.requireManageable(projectId);
        requireProjectAcceptsPhaseChanges(project);
        ProjectPhase phase = loadPhase(projectId, phaseId);
        requirePhaseNotTerminated(phase);
        phaseRepository.delete(phase);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private ProjectPhase loadPhase(UUID projectId, UUID phaseId) {
        ProjectPhase phase = phaseRepository.findById(phaseId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_PHASE_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (!phase.getProjectId().equals(projectId)) {   // phase must belong to the path's project
            throw new CustomException(ErrorCode.PROJECT_PHASE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return phase;
    }

    /** No phase actions once the project is TERMINATED or ARCHIVED (phases are then frozen). */
    private void requireProjectAcceptsPhaseChanges(Project project) {
        ProjectStatus s = project.getStatus();
        if (s == ProjectStatus.TERMINATED || s == ProjectStatus.ARCHIVED) {
            throw new CustomException(ErrorCode.PROJECT_INVALID_STATUS, HttpStatus.CONFLICT);
        }
    }

    private void requirePhaseNotTerminated(ProjectPhase phase) {
        if (phase.getStatus() == PhaseStatus.TERMINATED) {
            throw new CustomException(ErrorCode.PROJECT_PHASE_INVALID_STATUS, HttpStatus.CONFLICT);
        }
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new CustomException(ErrorCode.PROJECT_PHASE_INVALID_DATES, HttpStatus.BAD_REQUEST);
        }
    }

    /** A phase cannot start before its project — checked only once the project has a start date. */
    private void requireStartWithinProject(Project project, LocalDate phaseStart) {
        if (project.getStartDate() != null && phaseStart != null
                && phaseStart.isBefore(project.getStartDate())) {
            throw new CustomException(ErrorCode.PROJECT_PHASE_START_BEFORE_PROJECT, HttpStatus.BAD_REQUEST);
        }
    }

    /** The phases' weights (excluding {@code excludePhaseId}) plus {@code candidate} must be ≤ 100. */
    private void requireWeightWithinBudget(UUID projectId, UUID excludePhaseId, BigDecimal candidate) {
        BigDecimal others = phaseRepository.findByProjectId(projectId).stream()
                .filter(p -> excludePhaseId == null || !p.getId().equals(excludePhaseId))
                .map(ProjectPhase::getWeight)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (others.add(candidate).compareTo(MAX_TOTAL_WEIGHT) > 0) {
            throw new CustomException(ErrorCode.PROJECT_PHASE_WEIGHT_EXCEEDED, HttpStatus.CONFLICT);
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
