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
import ma.zakaria.tadbirbudget.entity.NomenclatureDefinition;
import ma.zakaria.tadbirbudget.entity.NomenclatureDefinitionLevel;
import ma.zakaria.tadbirbudget.entity.NomenclatureRubrique;
import ma.zakaria.tadbirbudget.entity.RubriqueAssignment;
import ma.zakaria.tadbirbudget.entity.enums.NomenclatureStatus;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.nomenclature.dto.CreateNomenclatureInput;
import ma.zakaria.tadbirbudget.nomenclature.dto.NomenclatureDefinitionLevelResponse;
import ma.zakaria.tadbirbudget.nomenclature.dto.NomenclatureResponse;
import ma.zakaria.tadbirbudget.nomenclature.dto.UpdateNomenclatureInput;
import ma.zakaria.tadbirbudget.repository.NomenclatureDefinitionLevelRepository;
import ma.zakaria.tadbirbudget.repository.NomenclatureDefinitionRepository;
import ma.zakaria.tadbirbudget.repository.NomenclatureRepository;
import ma.zakaria.tadbirbudget.repository.NomenclatureRubriqueRepository;
import ma.zakaria.tadbirbudget.repository.RubriqueAssignmentRepository;
import ma.zakaria.tadbirbudget.util.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages <b>nomenclatures</b> (the real trees) and their lifecycle: create against a definition
 * (DRAFT), fix (lock), archive, delete. The rubrique tree itself is managed by
 * {@link NomenclatureRubriqueService}. Reads are open; writes are admin/CdG.
 */
@Service
@RequiredArgsConstructor
public class NomenclatureService {

    private final NomenclatureRepository                 nomenclatureRepository;
    private final NomenclatureRubriqueRepository         rubriqueRepository;
    private final RubriqueAssignmentRepository           assignmentRepository;
    private final NomenclatureDefinitionRepository       definitionRepository;
    private final NomenclatureDefinitionLevelRepository  definitionLevelRepository;

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<NomenclatureResponse> listAll() {
        return nomenclatureRepository.findAllByOrderByNameAscVersionAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public NomenclatureResponse get(UUID id) {
        return toResponse(load(id));
    }

    /** All versions of the lineage the given nomenclature belongs to, oldest first. */
    @Transactional(readOnly = true)
    public List<NomenclatureResponse> versions(UUID id) {
        Nomenclature n = load(id);
        return nomenclatureRepository.findByLineageIdOrderByVersionAsc(n.getLineageId())
                .stream().map(this::toResponse).toList();
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @Transactional
    public NomenclatureResponse create(CreateNomenclatureInput input) {
        if (nomenclatureRepository.existsByNameIgnoreCase(input.getName().trim())) {
            throw new CustomException(ErrorCode.NOMENCLATURE_NAME_EXISTS, HttpStatus.BAD_REQUEST);
        }
        NomenclatureDefinition definition = definitionRepository.findById(input.getNomenclatureDefinitionId())
                .orElseThrow(() -> new CustomException(ErrorCode.NOMENCLATURE_DEFINITION_NOT_FOUND, HttpStatus.BAD_REQUEST));
        int depth = definitionLevelRepository
                .findByNomenclatureDefinitionIdOrderByPositionAsc(definition.getId()).size();

        Nomenclature saved = nomenclatureRepository.save(Nomenclature.builder()
                .name(input.getName().trim())
                .description(trimToNull(input.getDescription()))
                .nomenclatureDefinitionId(definition.getId())
                .definitionDepth(depth)
                .status(NomenclatureStatus.DRAFT)
                .version(1)
                .lineageId(UUID.randomUUID())   // a fresh lineage; clones reuse it
                .build());
        return toResponse(saved);
    }

    /**
     * Clone a FIXED nomenclature into a new DRAFT version of the same lineage. Rubriques are copied
     * with fresh ids (the tree structure preserved); the source is left untouched. When
     * {@code copyAssignments} is true, its rubrique→org-unit assignments are copied onto the new
     * version's matching rubriques.
     */
    @Transactional
    public NomenclatureResponse clone(UUID sourceId, boolean copyAssignments) {
        Nomenclature src = load(sourceId);
        if (src.getStatus() != NomenclatureStatus.FIXED) {
            throw new CustomException(ErrorCode.NOMENCLATURE_NOT_FIXED, HttpStatus.CONFLICT);
        }
        int nextVersion = nomenclatureRepository.maxVersionByLineageId(src.getLineageId()) + 1;

        Nomenclature v2 = nomenclatureRepository.save(Nomenclature.builder()
                .name(src.getName())
                .description(src.getDescription())
                .nomenclatureDefinitionId(src.getNomenclatureDefinitionId())
                .definitionDepth(src.getDefinitionDepth())
                .status(NomenclatureStatus.DRAFT)
                .version(nextVersion)
                .lineageId(src.getLineageId())
                .previousVersionId(src.getId())
                .build());

        // Copy rubriques in level order (parents before children) so parents are already remapped.
        Map<UUID, UUID> idMap = new HashMap<>();
        for (NomenclatureRubrique r : rubriqueRepository
                .findByNomenclatureIdOrderByLevelPositionAscCodeAsc(sourceId)) {
            NomenclatureRubrique copy = rubriqueRepository.save(NomenclatureRubrique.builder()
                    .nomenclatureId(v2.getId())
                    .parentId(r.getParentId() == null ? null : idMap.get(r.getParentId()))
                    .levelPosition(r.getLevelPosition())
                    .code(r.getCode())
                    .label(r.getLabel())
                    .leaf(r.isLeaf())
                    .build());
            idMap.put(r.getId(), copy.getId());
        }

        if (copyAssignments) {
            for (RubriqueAssignment a : assignmentRepository.findByNomenclatureId(sourceId)) {
                assignmentRepository.save(RubriqueAssignment.builder()
                        .rubriqueId(idMap.get(a.getRubriqueId()))
                        .nomenclatureId(v2.getId())
                        .orgUnitId(a.getOrgUnitId())
                        .assignedBy(SecurityUtils.getCurrentUsername())
                        .assignedAt(Instant.now())
                        .build());
            }
        }
        return toResponse(v2);
    }

    @Transactional
    public NomenclatureResponse update(UUID id, UpdateNomenclatureInput input) {
        Nomenclature n = load(id);
        if (input.getName() != null && !input.getName().trim().equalsIgnoreCase(n.getName())) {
            if (nomenclatureRepository.existsByNameIgnoreCase(input.getName().trim())) {
                throw new CustomException(ErrorCode.NOMENCLATURE_NAME_EXISTS, HttpStatus.BAD_REQUEST);
            }
            n.setName(input.getName().trim());
        } else if (input.getName() != null) {
            n.setName(input.getName().trim());
        }
        if (input.getDescription() != null) n.setDescription(trimToNull(input.getDescription()));
        return toResponse(nomenclatureRepository.save(n));
    }

    /** Lock the tree: DRAFT → FIXED. Requires at least one rubrique. */
    @Transactional
    public NomenclatureResponse fix(UUID id) {
        Nomenclature n = load(id);
        if (n.getStatus() != NomenclatureStatus.DRAFT) {
            throw new CustomException(ErrorCode.NOMENCLATURE_NOT_DRAFT, HttpStatus.CONFLICT);
        }
        if (rubriqueRepository.countByNomenclatureId(id) == 0) {
            throw new CustomException(ErrorCode.NOMENCLATURE_EMPTY, HttpStatus.BAD_REQUEST);
        }
        n.setStatus(NomenclatureStatus.FIXED);
        n.setFixedAt(Instant.now());
        n.setFixedBy(SecurityUtils.getCurrentUsername());
        return toResponse(nomenclatureRepository.save(n));
    }

    /** Retire a nomenclature (from any status). */
    @Transactional
    public NomenclatureResponse archive(UUID id) {
        Nomenclature n = load(id);
        n.setStatus(NomenclatureStatus.ARCHIVED);
        return toResponse(nomenclatureRepository.save(n));
    }

    /** Delete a DRAFT nomenclature and its rubriques. A FIXED one must be archived, not deleted. */
    @Transactional
    public void delete(UUID id) {
        Nomenclature n = load(id);
        if (n.getStatus() != NomenclatureStatus.DRAFT) {
            throw new CustomException(ErrorCode.NOMENCLATURE_NOT_DRAFT, HttpStatus.CONFLICT);
        }
        rubriqueRepository.deleteByNomenclatureId(id);
        nomenclatureRepository.delete(n);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private Nomenclature load(UUID id) {
        return nomenclatureRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.NOMENCLATURE_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private NomenclatureResponse toResponse(Nomenclature n) {
        String definitionName = definitionRepository.findById(n.getNomenclatureDefinitionId())
                .map(NomenclatureDefinition::getName).orElse(null);
        List<NomenclatureDefinitionLevel> levels = definitionLevelRepository
                .findByNomenclatureDefinitionIdOrderByPositionAsc(n.getNomenclatureDefinitionId());
        int last = levels.isEmpty() ? -1 : levels.get(levels.size() - 1).getPosition();
        List<NomenclatureDefinitionLevelResponse> levelDtos = levels.stream()
                .map(l -> NomenclatureDefinitionLevelResponse.from(l, l.getPosition() == last))
                .toList();
        long rubriqueCount = rubriqueRepository.countByNomenclatureId(n.getId());
        return NomenclatureResponse.from(n, definitionName, levelDtos, rubriqueCount);
    }

    /** Position → level name, for labelling rubriques (shared with the rubrique service). */
    Map<Integer, String> levelNamesByPosition(UUID nomenclatureDefinitionId) {
        return definitionLevelRepository.findByNomenclatureDefinitionIdOrderByPositionAsc(nomenclatureDefinitionId)
                .stream().collect(Collectors.toMap(NomenclatureDefinitionLevel::getPosition,
                        NomenclatureDefinitionLevel::getName, (a, b) -> a));
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
