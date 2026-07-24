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
import ma.zakaria.tadbirbudget.entity.enums.NomenclatureStatus;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.nomenclature.dto.CreateRubriqueInput;
import ma.zakaria.tadbirbudget.nomenclature.dto.RubriqueResponse;
import ma.zakaria.tadbirbudget.nomenclature.dto.UpdateRubriqueInput;
import ma.zakaria.tadbirbudget.repository.NomenclatureRepository;
import ma.zakaria.tadbirbudget.repository.NomenclatureRubriqueRepository;
import ma.zakaria.tadbirbudget.repository.RubriqueAssignmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the rubrique tree of a nomenclature — the real nodes (Fonctionnement → Marina → Achat).
 * All writes require the nomenclature to be {@code DRAFT}; each node's level and leaf flag are
 * derived from the parent + the definition depth, and codes are unique within the nomenclature.
 */
@Service
@RequiredArgsConstructor
public class NomenclatureRubriqueService {

    private final NomenclatureRepository         nomenclatureRepository;
    private final NomenclatureRubriqueRepository rubriqueRepository;
    private final RubriqueAssignmentRepository   assignmentRepository;
    private final NomenclatureService            nomenclatureService;   // shared level-name lookup

    @Transactional(readOnly = true)
    public List<RubriqueResponse> list(UUID nomenclatureId) {
        Nomenclature n = load(nomenclatureId);
        Map<Integer, String> levelNames = nomenclatureService.levelNamesByPosition(n.getNomenclatureDefinitionId());
        return rubriqueRepository.findByNomenclatureIdOrderByLevelPositionAscCodeAsc(nomenclatureId).stream()
                .map(r -> RubriqueResponse.from(r, levelNames.get(r.getLevelPosition())))
                .toList();
    }

    @Transactional
    public RubriqueResponse create(UUID nomenclatureId, CreateRubriqueInput input) {
        Nomenclature n = requireEditable(nomenclatureId);

        int levelPosition;
        UUID parentId = input.getParentId();
        if (parentId == null) {
            levelPosition = 1;
        } else {
            NomenclatureRubrique parent = loadRubrique(parentId);
            if (!parent.getNomenclatureId().equals(nomenclatureId)) {
                throw new CustomException(ErrorCode.RUBRIQUE_WRONG_NOMENCLATURE, HttpStatus.BAD_REQUEST);
            }
            if (parent.isLeaf()) {
                throw new CustomException(ErrorCode.RUBRIQUE_PARENT_IS_LEAF, HttpStatus.BAD_REQUEST);
            }
            levelPosition = parent.getLevelPosition() + 1;
        }

        // Code must be unique among siblings (same parent), not across the whole tree.
        if (siblingCodeExists(nomenclatureId, parentId, input.getCode().trim(), null)) {
            throw new CustomException(ErrorCode.RUBRIQUE_CODE_EXISTS, HttpStatus.BAD_REQUEST);
        }

        NomenclatureRubrique saved = rubriqueRepository.save(NomenclatureRubrique.builder()
                .nomenclatureId(nomenclatureId)
                .parentId(parentId)
                .levelPosition(levelPosition)
                .code(input.getCode().trim())
                .label(input.getLabel().trim())
                .leaf(levelPosition == n.getDefinitionDepth())
                .build());

        String levelName = nomenclatureService.levelNamesByPosition(n.getNomenclatureDefinitionId())
                .get(levelPosition);
        return RubriqueResponse.from(saved, levelName);
    }

    @Transactional
    public RubriqueResponse update(UUID nomenclatureId, UUID rubriqueId, UpdateRubriqueInput input) {
        Nomenclature n = requireEditable(nomenclatureId);
        NomenclatureRubrique r = loadRubriqueOf(nomenclatureId, rubriqueId);

        if (input.getCode() != null && !input.getCode().trim().equalsIgnoreCase(r.getCode())) {
            // Unique among siblings (same parent), excluding this rubrique itself.
            if (siblingCodeExists(nomenclatureId, r.getParentId(), input.getCode().trim(), r.getId())) {
                throw new CustomException(ErrorCode.RUBRIQUE_CODE_EXISTS, HttpStatus.BAD_REQUEST);
            }
            r.setCode(input.getCode().trim());
        } else if (input.getCode() != null) {
            r.setCode(input.getCode().trim());
        }
        if (input.getLabel() != null) r.setLabel(input.getLabel().trim());

        String levelName = nomenclatureService.levelNamesByPosition(n.getNomenclatureDefinitionId())
                .get(r.getLevelPosition());
        return RubriqueResponse.from(rubriqueRepository.save(r), levelName);
    }

    @Transactional
    public void delete(UUID nomenclatureId, UUID rubriqueId) {
        requireEditable(nomenclatureId);
        NomenclatureRubrique r = loadRubriqueOf(nomenclatureId, rubriqueId);
        if (rubriqueRepository.existsByParentId(rubriqueId)) {
            throw new CustomException(ErrorCode.RUBRIQUE_HAS_CHILDREN, HttpStatus.BAD_REQUEST);
        }
        // References are by id — deleting is safe only if nothing points at this rubrique.
        if (assignmentRepository.existsByRubriqueId(rubriqueId)) {
            throw new CustomException(ErrorCode.RUBRIQUE_HAS_ASSIGNMENTS, HttpStatus.BAD_REQUEST);
        }
        rubriqueRepository.delete(r);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private Nomenclature load(UUID nomenclatureId) {
        return nomenclatureRepository.findById(nomenclatureId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOMENCLATURE_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    /**
     * A rubrique tree is editable while DRAFT <b>or</b> FIXED — FIXED allows safe evolution (add,
     * rename, guarded delete); only ARCHIVED is read-only. Structural moves are still unsupported.
     */
    private Nomenclature requireEditable(UUID nomenclatureId) {
        Nomenclature n = load(nomenclatureId);
        if (n.getStatus() == NomenclatureStatus.ARCHIVED) {
            throw new CustomException(ErrorCode.NOMENCLATURE_ARCHIVED, HttpStatus.CONFLICT);
        }
        return n;
    }

    /**
     * True if another rubrique with the same code already exists among the siblings (same
     * nomenclature + same parent; roots share the null parent). {@code excludeId} skips the
     * rubrique being updated.
     */
    private boolean siblingCodeExists(UUID nomenclatureId, UUID parentId, String code, UUID excludeId) {
        if (parentId == null) {
            return excludeId == null
                    ? rubriqueRepository.existsByNomenclatureIdAndParentIdIsNullAndCodeIgnoreCase(nomenclatureId, code)
                    : rubriqueRepository.existsByNomenclatureIdAndParentIdIsNullAndCodeIgnoreCaseAndIdNot(
                            nomenclatureId, code, excludeId);
        }
        return excludeId == null
                ? rubriqueRepository.existsByNomenclatureIdAndParentIdAndCodeIgnoreCase(nomenclatureId, parentId, code)
                : rubriqueRepository.existsByNomenclatureIdAndParentIdAndCodeIgnoreCaseAndIdNot(
                        nomenclatureId, parentId, code, excludeId);
    }

    private NomenclatureRubrique loadRubrique(UUID rubriqueId) {
        return rubriqueRepository.findById(rubriqueId)
                .orElseThrow(() -> new CustomException(ErrorCode.RUBRIQUE_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private NomenclatureRubrique loadRubriqueOf(UUID nomenclatureId, UUID rubriqueId) {
        NomenclatureRubrique r = loadRubrique(rubriqueId);
        if (!r.getNomenclatureId().equals(nomenclatureId)) {
            throw new CustomException(ErrorCode.RUBRIQUE_WRONG_NOMENCLATURE, HttpStatus.BAD_REQUEST);
        }
        return r;
    }
}
