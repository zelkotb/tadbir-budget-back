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
package ma.zakaria.tadbirbudget.organisation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.entity.OrgUnit;
import ma.zakaria.tadbirbudget.entity.User;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.organisation.dto.CreateOrgUnitInput;
import ma.zakaria.tadbirbudget.organisation.dto.OrgUnitResponse;
import ma.zakaria.tadbirbudget.organisation.dto.OrgUnitUserResponse;
import ma.zakaria.tadbirbudget.organisation.dto.UpdateOrgUnitInput;
import ma.zakaria.tadbirbudget.repository.OrgUnitRepository;
import ma.zakaria.tadbirbudget.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Organisation-structure management. The tree is freely nested (any {@code kind} under any
 * {@code kind}); each node carries a materialized path so subtree reads are one LIKE query,
 * and moving a node rebases its whole subtree's paths in a single bulk statement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgUnitService {

    private final OrgUnitRepository orgUnitRepository;
    private final UserRepository    userRepository;

    // ── Reads ─────────────────────────────────────────────────────────────────

    /** Every unit, tree order (parents always before their children). */
    @Transactional(readOnly = true)
    public List<OrgUnitResponse> listAll() {
        return toResponses(orgUnitRepository.findAllByOrderByPathAsc());
    }

    @Transactional(readOnly = true)
    public OrgUnitResponse get(UUID id) {
        OrgUnit unit = load(id);
        String managerName = unit.getManagerId() == null ? null
                : userRepository.findById(unit.getManagerId()).map(User::getFullName).orElse(null);
        return OrgUnitResponse.from(unit, managerName);
    }

    /** The unit and all its descendants, tree order. */
    @Transactional(readOnly = true)
    public List<OrgUnitResponse> subtree(UUID id) {
        OrgUnit unit = load(id);
        return toResponses(orgUnitRepository.findByPathStartingWithOrderByPathAsc(unit.getPath()));
    }

    /** Users assigned to the unit — directly, or anywhere in its subtree. */
    @Transactional(readOnly = true)
    public List<OrgUnitUserResponse> users(UUID id, boolean includeSubtree) {
        OrgUnit unit = load(id);
        List<User> users;
        if (includeSubtree) {
            List<UUID> unitIds = orgUnitRepository
                    .findByPathStartingWithOrderByPathAsc(unit.getPath())
                    .stream().map(OrgUnit::getId).toList();
            users = userRepository.findByOrgUnitIdInOrderByFullNameAsc(unitIds);
        } else {
            users = userRepository.findByOrgUnitIdOrderByFullNameAsc(id);
        }
        return users.stream().map(OrgUnitUserResponse::from).toList();
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @Transactional
    public OrgUnitResponse create(CreateOrgUnitInput input) {
        OrgUnit parent = input.getParentId() == null ? null : load(input.getParentId());
        validateManager(input.getManagerId());

        OrgUnit unit = OrgUnit.builder()
                .name(input.getName())
                .kind(input.getKind())
                .parentId(parent == null ? null : parent.getId())
                .managerId(input.getManagerId())
                .path("/")   // placeholder — real path needs the generated id, set just below
                .depth(parent == null ? 0 : parent.getDepth() + 1)
                .build();
        orgUnitRepository.save(unit);   // assigns the UUID
        unit.setPath((parent == null ? "/" : parent.getPath()) + unit.getId() + "/");
        return OrgUnitResponse.from(unit, null);
    }

    @Transactional
    public OrgUnitResponse update(UUID id, UpdateOrgUnitInput input) {
        OrgUnit unit = load(id);

        if (input.getName() != null)   unit.setName(input.getName());
        if (input.getKind() != null)   unit.setKind(input.getKind());
        if (input.getActive() != null) unit.setActive(input.getActive());

        if (input.isClearManager()) {
            unit.setManagerId(null);
        } else if (input.getManagerId() != null) {
            validateManager(input.getManagerId());
            unit.setManagerId(input.getManagerId());
        }

        boolean move = input.isMoveToRoot() || input.getParentId() != null;
        if (move) {
            if (input.isMoveToRoot() && input.getParentId() != null) {
                throw new CustomException(ErrorCode.INVALID_VALUE, HttpStatus.BAD_REQUEST);
            }
            moveUnit(unit, input.isMoveToRoot() ? null : input.getParentId());
            return get(id);   // context was cleared by the bulk path rebase — reload
        }
        return OrgUnitResponse.from(orgUnitRepository.save(unit),
                resolveManagerName(unit.getManagerId()));
    }

    /**
     * Deletes a unit. Guarded: refused while the unit still has children or assigned users —
     * re-parent or re-assign them first (keeps the tree and user links always consistent).
     */
    @Transactional
    public void delete(UUID id) {
        OrgUnit unit = load(id);
        if (orgUnitRepository.existsByParentId(id)) {
            throw new CustomException(ErrorCode.ORG_UNIT_HAS_CHILDREN, HttpStatus.BAD_REQUEST);
        }
        if (userRepository.existsByOrgUnitId(id)) {
            throw new CustomException(ErrorCode.ORG_UNIT_HAS_USERS, HttpStatus.BAD_REQUEST);
        }
        orgUnitRepository.delete(unit);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /** Re-parent {@code unit} (null = to root) and rebase the paths of its whole subtree. */
    private void moveUnit(OrgUnit unit, UUID newParentId) {
        OrgUnit newParent = newParentId == null ? null : load(newParentId);
        if (newParent != null) {
            if (newParent.getId().equals(unit.getId())
                    || newParent.getPath().startsWith(unit.getPath())) {
                // the target parent is the node itself or one of its descendants
                throw new CustomException(ErrorCode.ORG_UNIT_CYCLE, HttpStatus.BAD_REQUEST);
            }
        }
        String oldPrefix = unit.getPath();
        String newPrefix = (newParent == null ? "/" : newParent.getPath()) + unit.getId() + "/";
        int newDepth   = newParent == null ? 0 : newParent.getDepth() + 1;
        int depthDelta = newDepth - unit.getDepth();

        unit.setParentId(newParent == null ? null : newParent.getId());
        orgUnitRepository.saveAndFlush(unit);   // audited business change: the parent link

        int rebased = orgUnitRepository.rebasePaths(oldPrefix, oldPrefix.length(), newPrefix, depthDelta);
        log.info("Moved org unit {} under {} — {} node(s) rebased", unit.getId(),
                newParent == null ? "<root>" : newParent.getId(), rebased);
    }

    private OrgUnit load(UUID id) {
        return orgUnitRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.ORG_UNIT_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private void validateManager(UUID managerId) {
        if (managerId != null && !userRepository.existsById(managerId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND, HttpStatus.BAD_REQUEST);
        }
    }

    private String resolveManagerName(UUID managerId) {
        return managerId == null ? null
                : userRepository.findById(managerId).map(User::getFullName).orElse(null);
    }

    /** Maps units to responses, resolving all manager names in one query (no N+1). */
    private List<OrgUnitResponse> toResponses(List<OrgUnit> units) {
        List<UUID> managerIds = units.stream()
                .map(OrgUnit::getManagerId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> names = managerIds.isEmpty() ? Map.of()
                : userRepository.findAllById(managerIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a));
        return units.stream()
                // guard the null key: Map.of() (empty, immutable) rejects null keys in get()
                .map(u -> OrgUnitResponse.from(u,
                        u.getManagerId() == null ? null : names.get(u.getManagerId())))
                .toList();
    }
}
