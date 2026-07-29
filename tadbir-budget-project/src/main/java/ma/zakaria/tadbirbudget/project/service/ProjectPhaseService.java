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
 * Phases and sous-phases of a project (how it is followed step by step). A sous-phase is a phase with
 * a parent (two levels only) — same fields, same rules. Authorization is delegated to
 * {@link ProjectService} (read to view, manage — chef or manager-in-scope — to write).
 *
 * <p>Status moves forward only ({@code CREATED → ACTIVE → TERMINATED}) or is cancelled
 * ({@code CREATED/ACTIVE → CANCELLED}); closed (terminated/cancelled) phases are read-only. Siblings'
 * weights (non-cancelled) may never sum to more than 100. A parent phase's completion is <b>derived</b>
 * from its non-cancelled sous-phases (Σ weight·completion / 100) and kept denormalized; leaf phases
 * carry their own. Starting a sous-phase needs its parent ACTIVE; terminating a parent needs all its
 * sous-phases closed; cancelling a parent cascades to its open sous-phases.
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
        return phaseRepository.findByProjectIdAndParentPhaseIdIsNullOrderByStartDateAscCreatedAtAsc(projectId)
                .stream().map(PhaseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PhaseResponse> listSubPhases(UUID projectId, UUID parentId) {
        projectService.requireReadable(projectId);
        loadPhase(projectId, parentId);   // parent must exist and belong to the project
        return phaseRepository.findByParentPhaseIdOrderByStartDateAscCreatedAtAsc(parentId)
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
        return PhaseResponse.from(buildAndSave(project, projectId, null, in));
    }

    @Transactional
    public PhaseResponse createSubPhase(UUID projectId, UUID parentId, CreatePhaseInput in) {
        Project project = projectService.requireManageable(projectId);
        requireProjectAcceptsPhaseChanges(project);
        ProjectPhase parent = loadPhase(projectId, parentId);
        if (parent.getParentPhaseId() != null) {                 // two levels only
            throw new CustomException(ErrorCode.PROJECT_SUBPHASE_NESTING, HttpStatus.CONFLICT);
        }
        requirePhaseNotClosed(parent);                           // can't add a sous-phase to a closed phase
        requireWithinParent(parent, in.getStartDate(), in.getEndDate());
        ProjectPhase sub = buildAndSave(project, projectId, parentId, in);
        recomputeParentCompletion(parentId);
        return PhaseResponse.from(sub);
    }

    @Transactional
    public PhaseResponse update(UUID projectId, UUID phaseId, UpdatePhaseInput in) {
        Project project = projectService.requireManageable(projectId);
        requireProjectAcceptsPhaseChanges(project);
        ProjectPhase phase = loadPhase(projectId, phaseId);
        requirePhaseNotClosed(phase);

        if (in.getTitle() != null)       phase.setTitle(in.getTitle().trim());
        if (in.getDescription() != null) phase.setDescription(trimToNull(in.getDescription()));
        if (in.getStartDate() != null)   phase.setStartDate(in.getStartDate());
        if (in.getEndDate() != null)     phase.setEndDate(in.getEndDate());
        validateDates(phase.getStartDate(), phase.getEndDate());
        requireStartWithinProject(project, phase.getStartDate());
        if (phase.getParentPhaseId() != null) {                  // a sous-phase stays inside its parent
            requireWithinParent(loadPhase(projectId, phase.getParentPhaseId()),
                    phase.getStartDate(), phase.getEndDate());
        } else if (phaseRepository.existsByParentPhaseId(phaseId)) {   // a parent must still contain its sous-phases
            requireContainsSubPhases(phaseId, phase.getStartDate(), phase.getEndDate());
        }
        if (in.getWeight() != null) {
            requireWeightWithinBudget(projectId, phase.getParentPhaseId(), phaseId, in.getWeight());
            phase.setWeight(in.getWeight());
        }
        // A parent phase's completion is derived from its sous-phases — ignore any manual value.
        if (in.getCompletion() != null && !phaseRepository.existsByParentPhaseId(phaseId)) {
            phase.setCompletion(in.getCompletion());
            // Recording a positive advancement on a not-started phase starts it.
            if (phase.getStatus() == PhaseStatus.CREATED
                    && in.getCompletion().compareTo(BigDecimal.ZERO) > 0) {
                requireContainerActive(project, phase);
                phase.setStatus(PhaseStatus.ACTIVE);
            }
        }
        ProjectPhase saved = phaseRepository.save(phase);
        if (phase.getParentPhaseId() != null) {
            recomputeParentCompletion(phase.getParentPhaseId());
        }
        return PhaseResponse.from(saved);
    }

    /** Move a phase/sous-phase: CREATED → ACTIVE → TERMINATED, or CREATED/ACTIVE → CANCELLED. */
    @Transactional
    public PhaseResponse changeStatus(UUID projectId, UUID phaseId, PhaseStatus target) {
        Project project = projectService.requireManageable(projectId);
        requireProjectAcceptsPhaseChanges(project);
        ProjectPhase phase = loadPhase(projectId, phaseId);
        requireValidTransition(phase.getStatus(), target);

        if (target == PhaseStatus.ACTIVE) {
            requireContainerActive(project, phase);
        }
        if (target == PhaseStatus.TERMINATED) {
            if (phaseRepository.existsByParentPhaseId(phaseId)) {
                requireAllSubPhasesClosed(phaseId);   // parent completion stays the derived rollup
            } else {
                phase.setCompletion(FULL_COMPLETION); // leaf phase → 100%
            }
        }
        if (target == PhaseStatus.CANCELLED) {
            cascadeCancelSubPhases(phaseId);
        }
        phase.setStatus(target);
        ProjectPhase saved = phaseRepository.save(phase);
        if (phase.getParentPhaseId() != null) {
            recomputeParentCompletion(phase.getParentPhaseId());
        }
        return PhaseResponse.from(saved);
    }

    @Transactional
    public void delete(UUID projectId, UUID phaseId) {
        Project project = projectService.requireManageable(projectId);
        requireProjectAcceptsPhaseChanges(project);
        ProjectPhase phase = loadPhase(projectId, phaseId);
        requirePhaseNotClosed(phase);
        UUID parentId = phase.getParentPhaseId();
        phaseRepository.delete(phase);   // FK on delete cascade removes sous-phases
        phaseRepository.flush();
        if (parentId != null) {
            recomputeParentCompletion(parentId);
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private ProjectPhase buildAndSave(Project project, UUID projectId, UUID parentId, CreatePhaseInput in) {
        validateDates(in.getStartDate(), in.getEndDate());
        requireStartWithinProject(project, in.getStartDate());
        BigDecimal weight = nz(in.getWeight());
        requireWeightWithinBudget(projectId, parentId, null, weight);

        return phaseRepository.save(ProjectPhase.builder()
                .projectId(projectId)
                .parentPhaseId(parentId)
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
    }

    private ProjectPhase loadPhase(UUID projectId, UUID phaseId) {
        ProjectPhase phase = phaseRepository.findById(phaseId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_PHASE_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (!phase.getProjectId().equals(projectId)) {
            throw new CustomException(ErrorCode.PROJECT_PHASE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return phase;
    }

    /** Recompute and store a parent phase's completion from its non-cancelled sous-phases. */
    private void recomputeParentCompletion(UUID parentId) {
        ProjectPhase parent = phaseRepository.findById(parentId).orElse(null);
        if (parent == null) {
            return;
        }
        double rolledUp = phaseRepository.findByParentPhaseId(parentId).stream()
                .filter(s -> s.getStatus() != PhaseStatus.CANCELLED)
                .mapToDouble(s -> dbl(s.getWeight()) * dbl(s.getCompletion()) / 100.0)
                .sum();
        parent.setCompletion(BigDecimal.valueOf(round2(rolledUp)));
        phaseRepository.save(parent);
    }

    private void cascadeCancelSubPhases(UUID parentId) {
        for (ProjectPhase sub : phaseRepository.findByParentPhaseId(parentId)) {
            if (!isClosed(sub.getStatus())) {
                sub.setStatus(PhaseStatus.CANCELLED);
                phaseRepository.save(sub);
            }
        }
    }

    /** A sous-phase can start only when its parent is ACTIVE; a top-level phase, when the project is. */
    private void requireContainerActive(Project project, ProjectPhase phase) {
        if (phase.getParentPhaseId() == null) {
            if (project.getStatus() != ProjectStatus.ACTIVE) {
                throw new CustomException(ErrorCode.PROJECT_NOT_ACTIVE, HttpStatus.CONFLICT);
            }
        } else {
            ProjectPhase parent = loadPhase(phase.getProjectId(), phase.getParentPhaseId());
            if (parent.getStatus() != PhaseStatus.ACTIVE) {
                throw new CustomException(ErrorCode.PROJECT_PHASE_PARENT_NOT_ACTIVE, HttpStatus.CONFLICT);
            }
        }
    }

    private void requireAllSubPhasesClosed(UUID parentId) {
        boolean open = phaseRepository.findByParentPhaseId(parentId).stream()
                .anyMatch(s -> !isClosed(s.getStatus()));
        if (open) {
            throw new CustomException(ErrorCode.PROJECT_PHASE_HAS_OPEN_SUBPHASES, HttpStatus.CONFLICT);
        }
    }

    private void requireValidTransition(PhaseStatus from, PhaseStatus to) {
        boolean ok = switch (from) {
            case CREATED             -> to == PhaseStatus.ACTIVE || to == PhaseStatus.CANCELLED;
            case ACTIVE              -> to == PhaseStatus.TERMINATED || to == PhaseStatus.CANCELLED;
            case TERMINATED, CANCELLED -> false;   // terminal
        };
        if (!ok) {
            throw new CustomException(ErrorCode.PROJECT_PHASE_INVALID_STATUS, HttpStatus.CONFLICT);
        }
    }

    private boolean isClosed(PhaseStatus status) {
        return status == PhaseStatus.TERMINATED || status == PhaseStatus.CANCELLED;
    }

    private void requireProjectAcceptsPhaseChanges(Project project) {
        ProjectStatus s = project.getStatus();
        if (s == ProjectStatus.TERMINATED || s == ProjectStatus.ARCHIVED) {
            throw new CustomException(ErrorCode.PROJECT_INVALID_STATUS, HttpStatus.CONFLICT);
        }
    }

    private void requirePhaseNotClosed(ProjectPhase phase) {
        if (isClosed(phase.getStatus())) {
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

    /** A sous-phase's schedule must lie within its parent phase's schedule. */
    private void requireWithinParent(ProjectPhase parent, LocalDate start, LocalDate end) {
        LocalDate ps = parent.getStartDate();
        LocalDate pe = parent.getEndDate();
        if ((ps != null && start != null && start.isBefore(ps))
                || (pe != null && end != null && end.isAfter(pe))) {
            throw new CustomException(ErrorCode.PROJECT_SUBPHASE_OUTSIDE_PARENT, HttpStatus.BAD_REQUEST);
        }
    }

    /** A parent phase's new schedule must still contain every non-cancelled sous-phase. */
    private void requireContainsSubPhases(UUID parentId, LocalDate start, LocalDate end) {
        for (ProjectPhase sub : phaseRepository.findByParentPhaseId(parentId)) {
            if (sub.getStatus() == PhaseStatus.CANCELLED) {
                continue;
            }
            if ((start != null && sub.getStartDate() != null && sub.getStartDate().isBefore(start))
                    || (end != null && sub.getEndDate() != null && sub.getEndDate().isAfter(end))) {
                throw new CustomException(ErrorCode.PROJECT_SUBPHASE_OUTSIDE_PARENT, HttpStatus.BAD_REQUEST);
            }
        }
    }

    /** Non-cancelled siblings (same parent) plus {@code candidate} must weigh ≤ 100. */
    private void requireWeightWithinBudget(UUID projectId, UUID parentId, UUID excludeId, BigDecimal candidate) {
        List<ProjectPhase> siblings = parentId == null
                ? phaseRepository.findByProjectIdAndParentPhaseIdIsNullOrderByStartDateAscCreatedAtAsc(projectId)
                : phaseRepository.findByParentPhaseId(parentId);
        BigDecimal others = siblings.stream()
                .filter(p -> excludeId == null || !p.getId().equals(excludeId))
                .filter(p -> p.getStatus() != PhaseStatus.CANCELLED)
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

    private double dbl(BigDecimal v) {
        return v != null ? v.doubleValue() : 0d;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
