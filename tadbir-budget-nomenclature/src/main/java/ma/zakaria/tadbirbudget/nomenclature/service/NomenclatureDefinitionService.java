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
import ma.zakaria.tadbirbudget.entity.NomenclatureDefinition;
import ma.zakaria.tadbirbudget.entity.NomenclatureDefinitionLevel;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.nomenclature.dto.CreateNomenclatureDefinitionInput;
import ma.zakaria.tadbirbudget.nomenclature.dto.NomenclatureDefinitionResponse;
import ma.zakaria.tadbirbudget.nomenclature.dto.UpdateNomenclatureDefinitionInput;
import ma.zakaria.tadbirbudget.repository.NomenclatureDefinitionLevelRepository;
import ma.zakaria.tadbirbudget.repository.NomenclatureDefinitionRepository;
import ma.zakaria.tadbirbudget.repository.NomenclatureRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages budget <b>nomenclature definitions</b> (the level templates: Chapitre → Article → … →
 * Ligne). A definition is created with its ordered level names in one shot; updating the level
 * list replaces it wholesale. The deepest level is the leaf ("ligne budgétaire"). Reference data —
 * reads are open, writes are admin/CdG.
 */
@Service
@RequiredArgsConstructor
public class NomenclatureDefinitionService {

    private final NomenclatureDefinitionRepository      definitionRepository;
    private final NomenclatureDefinitionLevelRepository levelRepository;
    private final NomenclatureRepository                nomenclatureRepository;

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<NomenclatureDefinitionResponse> listAll() {
        List<NomenclatureDefinition> definitions = definitionRepository.findAllByOrderByNameAsc();
        if (definitions.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<NomenclatureDefinitionLevel>> levelsByDefinition = levelRepository
                .findByNomenclatureDefinitionIdInOrderByNomenclatureDefinitionIdAscPositionAsc(
                        definitions.stream().map(NomenclatureDefinition::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(NomenclatureDefinitionLevel::getNomenclatureDefinitionId));
        return definitions.stream()
                .map(d -> NomenclatureDefinitionResponse.from(d, levelsByDefinition.getOrDefault(d.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public NomenclatureDefinitionResponse get(UUID id) {
        NomenclatureDefinition definition = load(id);
        return NomenclatureDefinitionResponse.from(definition,
                levelRepository.findByNomenclatureDefinitionIdOrderByPositionAsc(id));
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @Transactional
    public NomenclatureDefinitionResponse create(CreateNomenclatureDefinitionInput input) {
        if (definitionRepository.existsByNameIgnoreCase(input.getName().trim())) {
            throw new CustomException(ErrorCode.NOMENCLATURE_DEFINITION_NAME_EXISTS, HttpStatus.BAD_REQUEST);
        }
        List<String> levelNames = sanitizeLevels(input.getLevels());

        NomenclatureDefinition definition = definitionRepository.save(NomenclatureDefinition.builder()
                .name(input.getName().trim())
                .description(trimToNull(input.getDescription()))
                .active(true)
                .build());
        List<NomenclatureDefinitionLevel> levels = writeLevels(definition.getId(), levelNames);
        return NomenclatureDefinitionResponse.from(definition, levels);
    }

    @Transactional
    public NomenclatureDefinitionResponse update(UUID id, UpdateNomenclatureDefinitionInput input) {
        NomenclatureDefinition definition = load(id);

        if (input.getName() != null && !input.getName().trim().equalsIgnoreCase(definition.getName())) {
            if (definitionRepository.existsByNameIgnoreCase(input.getName().trim())) {
                throw new CustomException(ErrorCode.NOMENCLATURE_DEFINITION_NAME_EXISTS, HttpStatus.BAD_REQUEST);
            }
            definition.setName(input.getName().trim());
        } else if (input.getName() != null) {
            definition.setName(input.getName().trim());   // same name, only casing/whitespace changed
        }
        if (input.getDescription() != null) definition.setDescription(trimToNull(input.getDescription()));
        if (input.getActive() != null)      definition.setActive(input.getActive());
        definitionRepository.save(definition);

        List<NomenclatureDefinitionLevel> levels;
        if (input.getLevels() != null) {
            List<String> levelNames = sanitizeLevels(input.getLevels());
            levelRepository.deleteByNomenclatureDefinitionId(id);
            levelRepository.flush();   // apply the delete before re-inserting (unique constraint)
            levels = writeLevels(id, levelNames);
        } else {
            levels = levelRepository.findByNomenclatureDefinitionIdOrderByPositionAsc(id);
        }
        return NomenclatureDefinitionResponse.from(definition, levels);
    }

    @Transactional
    public void delete(UUID id) {
        NomenclatureDefinition definition = load(id);
        // A definition can't be deleted while a real nomenclature is built on it.
        if (nomenclatureRepository.existsByNomenclatureDefinitionId(id)) {
            throw new CustomException(ErrorCode.NOMENCLATURE_DEFINITION_IN_USE, HttpStatus.CONFLICT);
        }
        levelRepository.deleteByNomenclatureDefinitionId(id);
        definitionRepository.delete(definition);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private NomenclatureDefinition load(UUID id) {
        return definitionRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.NOMENCLATURE_DEFINITION_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    /** Trims names, rejects empties and case-insensitive duplicates, returns them in order. */
    private List<String> sanitizeLevels(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new CustomException(ErrorCode.NOMENCLATURE_DEFINITION_NO_LEVELS, HttpStatus.BAD_REQUEST);
        }
        List<String> trimmed = raw.stream().map(s -> s == null ? "" : s.trim()).toList();
        if (trimmed.stream().anyMatch(String::isEmpty)) {
            throw new CustomException(ErrorCode.NOMENCLATURE_DEFINITION_NO_LEVELS, HttpStatus.BAD_REQUEST);
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String name : trimmed) {
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw new CustomException(ErrorCode.NOMENCLATURE_DEFINITION_LEVEL_DUPLICATE, HttpStatus.BAD_REQUEST);
            }
        }
        return trimmed;
    }

    /** Persists the ordered level names as positions 1..N for the given definition. */
    private List<NomenclatureDefinitionLevel> writeLevels(UUID definitionId, List<String> names) {
        List<NomenclatureDefinitionLevel> levels = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
            levels.add(NomenclatureDefinitionLevel.builder()
                    .nomenclatureDefinitionId(definitionId)
                    .position(i + 1)
                    .name(names.get(i))
                    .build());
        }
        return levelRepository.saveAll(levels);
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
