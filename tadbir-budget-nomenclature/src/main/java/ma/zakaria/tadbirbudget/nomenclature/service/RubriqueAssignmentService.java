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
package ma.zakaria.tadbirbudget.nomenclature.service;

import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.entity.Nomenclature;
import ma.zakaria.tadbirbudget.entity.NomenclatureRubrique;
import ma.zakaria.tadbirbudget.entity.OrgUnit;
import ma.zakaria.tadbirbudget.entity.RubriqueAssignment;
import ma.zakaria.tadbirbudget.entity.User;
import ma.zakaria.tadbirbudget.entity.enums.NomenclatureStatus;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.nomenclature.dto.CreateRubriqueAssignmentInput;
import ma.zakaria.tadbirbudget.nomenclature.dto.RubriqueAssignmentResponse;
import ma.zakaria.tadbirbudget.nomenclature.dto.RubriqueResponse;
import ma.zakaria.tadbirbudget.repository.NomenclatureRepository;
import ma.zakaria.tadbirbudget.repository.NomenclatureRubriqueRepository;
import ma.zakaria.tadbirbudget.repository.OrgUnitRepository;
import ma.zakaria.tadbirbudget.repository.RubriqueAssignmentRepository;
import ma.zakaria.tadbirbudget.util.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Rubrique → org-unit assignments, and the "usable rubriques" resolver used by project creation.
 * Assignments are CG/admin-managed and only allowed on a FIXED nomenclature. A user may use a
 * rubrique when it (or an ancestor) is assigned to their org unit or any ancestor org unit; the
 * grant covers the assigned node's whole subtree.
 */
@Service
@RequiredArgsConstructor
public class RubriqueAssignmentService {

    private final RubriqueAssignmentRepository   assignmentRepository;
    private final NomenclatureRepository         nomenclatureRepository;
    private final NomenclatureRubriqueRepository rubriqueRepository;
    private final OrgUnitRepository              orgUnitRepository;
    private final NomenclatureService            nomenclatureService;   // shared level-name lookup

    // ── Manage assignments (CG/admin) ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RubriqueAssignmentResponse> listAssignments(UUID nomenclatureId, UUID orgUnitId) {
        Nomenclature n = loadNomenclature(nomenclatureId);
        List<RubriqueAssignment> assignments = orgUnitId == null
                ? assignmentRepository.findByNomenclatureId(nomenclatureId)
                : assignmentRepository.findByNomenclatureIdAndOrgUnitId(nomenclatureId, orgUnitId);
        if (assignments.isEmpty()) {
            return List.of();
        }
        Map<UUID, NomenclatureRubrique> rubriques = byId(rubriqueRepository.findAllById(
                assignments.stream().map(RubriqueAssignment::getRubriqueId).toList()));
        Map<UUID, String> orgNames = orgUnitRepository.findAllById(
                        assignments.stream().map(RubriqueAssignment::getOrgUnitId).toList())
                .stream().collect(Collectors.toMap(OrgUnit::getId, OrgUnit::getName, (a, b) -> a));
        Map<Integer, String> levelNames = nomenclatureService.levelNamesByPosition(n.getNomenclatureDefinitionId());

        return assignments.stream().map(a -> {
            NomenclatureRubrique r = rubriques.get(a.getRubriqueId());
            return new RubriqueAssignmentResponse(
                    a.getId(), a.getRubriqueId(),
                    r == null ? null : r.getCode(), r == null ? null : r.getLabel(),
                    r == null ? 0 : r.getLevelPosition(),
                    r == null ? null : levelNames.get(r.getLevelPosition()),
                    r != null && r.isLeaf(),
                    a.getOrgUnitId(), orgNames.get(a.getOrgUnitId()),
                    a.getAssignedBy(), a.getAssignedAt());
        }).toList();
    }

    @Transactional
    public RubriqueAssignmentResponse assign(UUID nomenclatureId, CreateRubriqueAssignmentInput input) {
        Nomenclature n = loadNomenclature(nomenclatureId);
        if (n.getStatus() != NomenclatureStatus.FIXED) {
            throw new CustomException(ErrorCode.NOMENCLATURE_NOT_FIXED, HttpStatus.CONFLICT);
        }
        NomenclatureRubrique rubrique = rubriqueRepository.findById(input.getRubriqueId())
                .orElseThrow(() -> new CustomException(ErrorCode.RUBRIQUE_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (!rubrique.getNomenclatureId().equals(nomenclatureId)) {
            throw new CustomException(ErrorCode.RUBRIQUE_WRONG_NOMENCLATURE, HttpStatus.BAD_REQUEST);
        }
        if (!orgUnitRepository.existsById(input.getOrgUnitId())) {
            throw new CustomException(ErrorCode.ORG_UNIT_NOT_FOUND, HttpStatus.BAD_REQUEST);
        }
        if (assignmentRepository.existsByRubriqueIdAndOrgUnitId(input.getRubriqueId(), input.getOrgUnitId())) {
            throw new CustomException(ErrorCode.RUBRIQUE_ASSIGNMENT_EXISTS, HttpStatus.BAD_REQUEST);
        }
        assignmentRepository.save(RubriqueAssignment.builder()
                .rubriqueId(input.getRubriqueId())
                .nomenclatureId(nomenclatureId)
                .orgUnitId(input.getOrgUnitId())
                .assignedBy(SecurityUtils.getCurrentUsername())
                .assignedAt(Instant.now())
                .build());
        // Re-list is cheap and gives the fully-resolved row back.
        return listAssignments(nomenclatureId, input.getOrgUnitId()).stream()
                .filter(a -> a.rubriqueId().equals(input.getRubriqueId()))
                .findFirst().orElseThrow();
    }

    @Transactional
    public void unassign(UUID nomenclatureId, UUID assignmentId) {
        RubriqueAssignment a = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new CustomException(ErrorCode.RUBRIQUE_ASSIGNMENT_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (!a.getNomenclatureId().equals(nomenclatureId)) {
            throw new CustomException(ErrorCode.RUBRIQUE_ASSIGNMENT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        assignmentRepository.delete(a);
    }

    // ── Resolve usable rubriques (project creation) ─────────────────────────────

    /**
     * The rubriques a user may attach to a project in this nomenclature: the assigned nodes (granted
     * to their org unit or any ancestor) plus each node's whole subtree. {@code orgUnitOverride} lets
     * an admin preview another unit; otherwise the caller's own org unit is used.
     */
    @Transactional(readOnly = true)
    public List<RubriqueResponse> usableRubriques(UUID nomenclatureId, UUID orgUnitOverride) {
        Nomenclature n = loadNomenclature(nomenclatureId);
        UUID targetOrgUnitId = orgUnitOverride != null ? orgUnitOverride : callerOrgUnitId();
        if (targetOrgUnitId == null) {
            return List.of();
        }
        OrgUnit target = orgUnitRepository.findById(targetOrgUnitId)
                .orElseThrow(() -> new CustomException(ErrorCode.ORG_UNIT_NOT_FOUND, HttpStatus.BAD_REQUEST));

        List<UUID> reachableUnits = orgUnitRepository.findSelfAndAncestorsByPath(target.getPath())
                .stream().map(OrgUnit::getId).toList();
        Set<UUID> assignedRubriqueIds = assignmentRepository
                .findByNomenclatureIdAndOrgUnitIdIn(nomenclatureId, reachableUnits)
                .stream().map(RubriqueAssignment::getRubriqueId).collect(Collectors.toSet());
        if (assignedRubriqueIds.isEmpty()) {
            return List.of();
        }

        List<NomenclatureRubrique> all = rubriqueRepository
                .findByNomenclatureIdOrderByLevelPositionAscCodeAsc(nomenclatureId);
        Map<UUID, List<NomenclatureRubrique>> childrenByParent = all.stream()
                .filter(r -> r.getParentId() != null)
                .collect(Collectors.groupingBy(NomenclatureRubrique::getParentId));

        // BFS from each assigned node down through its subtree.
        Set<UUID> usable = new LinkedHashSet<>();
        Deque<UUID> queue = new ArrayDeque<>(assignedRubriqueIds);
        while (!queue.isEmpty()) {
            UUID id = queue.poll();
            if (!usable.add(id)) {
                continue;
            }
            for (NomenclatureRubrique child : childrenByParent.getOrDefault(id, List.of())) {
                queue.add(child.getId());
            }
        }

        Map<Integer, String> levelNames = nomenclatureService.levelNamesByPosition(n.getNomenclatureDefinitionId());
        return all.stream()
                .filter(r -> usable.contains(r.getId()))
                .map(r -> RubriqueResponse.from(r, levelNames.get(r.getLevelPosition())))
                .toList();
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    private Nomenclature loadNomenclature(UUID id) {
        return nomenclatureRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.NOMENCLATURE_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private UUID callerOrgUnitId() {
        return SecurityUtils.getCurrentUser() instanceof User u ? u.getOrgUnitId() : null;
    }

    private Map<UUID, NomenclatureRubrique> byId(List<NomenclatureRubrique> rubriques) {
        return rubriques.stream().collect(Collectors.toMap(NomenclatureRubrique::getId, Function.identity()));
    }
}
