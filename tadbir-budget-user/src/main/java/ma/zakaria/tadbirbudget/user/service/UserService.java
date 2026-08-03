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
package ma.zakaria.tadbirbudget.user.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.constant.Permissions;
import ma.zakaria.tadbirbudget.constant.Roles;
import ma.zakaria.tadbirbudget.entity.OrgUnit;
import ma.zakaria.tadbirbudget.entity.User;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.repository.OrgUnitRepository;
import ma.zakaria.tadbirbudget.repository.UserRepository;
import ma.zakaria.tadbirbudget.user.dto.ChangePasswordInput;
import ma.zakaria.tadbirbudget.user.dto.CreateUserInput;
import ma.zakaria.tadbirbudget.user.dto.UpdateUserInput;
import ma.zakaria.tadbirbudget.user.dto.UserResponse;
import ma.zakaria.tadbirbudget.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    /** Roles an admin may assign when creating/updating an account. */
    private static final Set<String> ASSIGNABLE_ROLES = Set.of(
            Roles.ADMIN, Roles.EMPLOYEE, Roles.CELL_MANAGER, Roles.SERVICE_MANAGER, Roles.DEPARTMENT_MANAGER,
            Roles.DIRECTION_MANAGER, Roles.POLE_MANAGER, Roles.DIRECTION_GENERALE, Roles.CONTROLE_GESTION);

    /** Fine-grained permissions an admin may grant à la carte. */
    private static final Set<String> ASSIGNABLE_PERMISSIONS = Set.of(
            Permissions.BUDGET_DEFINITION, Permissions.BUDGET_NOMENCLATURE);

    private final UserRepository    userRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final PasswordEncoder   passwordEncoder;

    @Transactional(readOnly = true)
    public UserResponse getMe() {
        User caller = (User) SecurityUtils.getCurrentUser();
        return userRepository.findById(caller.getId())
                .map(u -> UserResponse.from(u, managerOf(u), orgUnitOf(u)))
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        User caller = (User) SecurityUtils.getCurrentUser();
        if (!caller.getRoles().contains(Roles.ADMIN) && !caller.getId().equals(id)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
        return userRepository.findById(id)
                .map(u -> UserResponse.from(u, managerOf(u), orgUnitOf(u)))
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    @Transactional
    public UserResponse updateUser(UUID targetId, UpdateUserInput input) {
        User caller = (User) SecurityUtils.getCurrentUser();
        boolean isAdmin      = caller.getRoles().contains(Roles.ADMIN);
        boolean isOwnAccount = caller.getId().equals(targetId);

        if (!isAdmin && !isOwnAccount) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }

        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (input.getUid() != null && !input.getUid().equals(user.getUid())) {
            if (userRepository.existsByUid(input.getUid())) {
                throw new CustomException(ErrorCode.UID_ALREADY_EXISTS, HttpStatus.BAD_REQUEST);
            }
            user.setUid(input.getUid());
        }
        if (input.getEmail()           != null) user.setEmail(input.getEmail());
        if (input.getFullName()        != null) user.setFullName(input.getFullName());
        if (input.getPhoneNumber()     != null) user.setPhoneNumber(input.getPhoneNumber());

        if (isAdmin && input.getRoles() != null && !input.getRoles().isEmpty()) {
            input.getRoles().forEach(role -> {
                if (!ASSIGNABLE_ROLES.contains(role)) {
                    throw new CustomException(ErrorCode.INVALID_ROLE, HttpStatus.BAD_REQUEST);
                }
            });
            user.setRoles(input.getRoles());
        }

        if (isAdmin && input.getPermissions() != null) {
            user.setPermissions(validatePermissions(input.getPermissions()));
        }

        if (isAdmin && input.getManagerId() != null) {
            validateManager(input.getManagerId(), targetId);
            user.setManagerId(input.getManagerId());
        }

        if (isAdmin && input.getOrgUnitId() != null) {
            validateOrgUnit(input.getOrgUnitId());
            user.setOrgUnitId(input.getOrgUnitId());
        }

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(String fullName, String email, String uid,
                                        Boolean enabled, List<String> roles,
                                        UUID orgUnitId, UUID managerId, Pageable pageable) {
        // Filtering by org unit returns everyone in that unit AND every unit below it (subtree).
        final List<UUID> orgSubtree = orgUnitId == null ? null : orgUnitSubtreeIds(orgUnitId);

        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (fullName        != null) predicates.add(cb.like(cb.lower(root.get("fullName")),
                                                                "%" + fullName.toLowerCase() + "%"));
            if (email           != null) predicates.add(cb.like(cb.lower(root.get("email")),
                                                                "%" + email.toLowerCase() + "%"));
            if (uid             != null) predicates.add(cb.like(cb.lower(root.get("uid")),
                                                                "%" + uid.toLowerCase() + "%"));
            if (enabled         != null) predicates.add(cb.equal(root.get("enabled"), enabled));
            if (managerId       != null) predicates.add(cb.equal(root.get("managerId"), managerId));
            if (orgSubtree      != null) predicates.add(orgSubtree.isEmpty()
                    ? cb.disjunction()                                  // unknown unit → no results
                    : root.get("orgUnitId").in(orgSubtree));
            if (roles != null && !roles.isEmpty()) {
                // OR: user must have at least one of the requested roles.
                // roles column is CSV ("ROLE_ADMIN,ROLE_INSTRUCTOR") — LIKE is safe because
                // role names are guaranteed not to be substrings of each other (Roles.java convention).
                List<Predicate> rolePredicates = roles.stream()
                        .map(role -> cb.like(root.get("roles"), "%" + role + "%"))
                        .toList();
                predicates.add(cb.or(rolePredicates.toArray(new Predicate[0])));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<User> page = userRepository.findAll(spec, pageable);
        Map<UUID, User> managers = loadManagers(page.getContent());
        Map<UUID, OrgUnit> orgUnits = loadOrgUnits(page.getContent());
        return page.map(u -> UserResponse.from(u,
                u.getManagerId() == null ? null : managers.get(u.getManagerId()),
                u.getOrgUnitId() == null ? null : orgUnits.get(u.getOrgUnitId())));
    }

    /** The org unit and every unit below it (subtree), by id; empty if the unit does not exist. */
    private List<UUID> orgUnitSubtreeIds(UUID orgUnitId) {
        return orgUnitRepository.findById(orgUnitId)
                .map(unit -> orgUnitRepository.findByPathStartingWithOrderByPathAsc(unit.getPath())
                        .stream().map(OrgUnit::getId).toList())
                .orElseGet(List::of);
    }

    /** Batch-loads the managers of the given users, so responses can show the manager's name. */
    private Map<UUID, User> loadManagers(List<User> users) {
        Set<UUID> managerIds = users.stream()
                .map(User::getManagerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (managerIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(managerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    /** Resolves a single user's manager (or null) for the detail views. */
    private User managerOf(User user) {
        return user.getManagerId() == null ? null
                : userRepository.findById(user.getManagerId()).orElse(null);
    }

    /** Resolves a single user's org unit (or null) so the response can show its name. */
    private OrgUnit orgUnitOf(User user) {
        return user.getOrgUnitId() == null ? null
                : orgUnitRepository.findById(user.getOrgUnitId()).orElse(null);
    }

    /** Batch-loads the org units of the given users, so responses can show the unit's name. */
    private Map<UUID, OrgUnit> loadOrgUnits(List<User> users) {
        Set<UUID> orgUnitIds = users.stream()
                .map(User::getOrgUnitId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (orgUnitIds.isEmpty()) {
            return Map.of();
        }
        return orgUnitRepository.findAllById(orgUnitIds).stream()
                .collect(Collectors.toMap(OrgUnit::getId, u -> u));
    }

    /** All users, for the manager (N+1) picker. */
    @Transactional(readOnly = true)
    public List<UserResponse> listStaff() {
        return userRepository.findStaff().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public User createUser(CreateUserInput input) {
        if (userRepository.existsByUid(input.getUid())) {
            throw new CustomException(ErrorCode.UID_ALREADY_EXISTS, HttpStatus.BAD_REQUEST);
        }
        if (userRepository.existsByEmail(input.getEmail())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS, HttpStatus.BAD_REQUEST);
        }

        List<String> roles = input.getRoles();
        roles.forEach(role -> {
            if (!ASSIGNABLE_ROLES.contains(role)) {
                throw new CustomException(ErrorCode.INVALID_ROLE, HttpStatus.BAD_REQUEST);
            }
        });

        List<String> permissions = validatePermissions(input.getPermissions());

        if (input.getManagerId() != null && !userRepository.existsById(input.getManagerId())) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND, HttpStatus.BAD_REQUEST);
        }
        validateOrgUnit(input.getOrgUnitId());

        return userRepository.save(User.builder()
                .uid(input.getUid())
                .fullName(input.getFullName())
                .phoneNumber(input.getPhoneNumber())
                .email(input.getEmail())
                .password(passwordEncoder.encode(input.getPassword()))
                .roles(roles)
                .permissions(permissions)
                .managerId(input.getManagerId())
                .orgUnitId(input.getOrgUnitId())
                .build());
    }

    /** Validates each requested permission against the assignable set; null → empty list. */
    private List<String> validatePermissions(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return new ArrayList<>();
        }
        permissions.forEach(p -> {
            if (!ASSIGNABLE_PERMISSIONS.contains(p)) {
                throw new CustomException(ErrorCode.INVALID_PERMISSION, HttpStatus.BAD_REQUEST);
            }
        });
        return permissions;
    }

    /** When provided, the org unit must exist. */
    private void validateOrgUnit(UUID orgUnitId) {
        if (orgUnitId != null && !orgUnitRepository.existsById(orgUnitId)) {
            throw new CustomException(ErrorCode.ORG_UNIT_NOT_FOUND, HttpStatus.BAD_REQUEST);
        }
    }

    /** A manager must be an existing, different user (no self-management). */
    private void validateManager(UUID managerId, UUID targetUserId) {
        if (managerId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.INVALID_VALUE, HttpStatus.BAD_REQUEST);
        }
        if (!userRepository.existsById(managerId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND, HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public void enableUser(UUID targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
        user.setEnabled(true);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);
    }

    @Transactional
    public void disableUser(UUID targetUserId) {
        User caller = (User) SecurityUtils.getCurrentUser();
        if (caller.getId().equals(targetUserId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
        user.setEnabled(false);
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(UUID targetUserId, ChangePasswordInput input) {
        User caller = (User) SecurityUtils.getCurrentUser();
        boolean isAdmin     = caller.getRoles().contains(Roles.ADMIN);
        boolean isOwnAccount = caller.getId().equals(targetUserId);

        if (!isAdmin && !isOwnAccount) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }

        // Non-admin must always provide currentPassword
        if (!isAdmin && (input.getCurrentPassword() == null || input.getCurrentPassword().isBlank())) {
            throw new CustomException(ErrorCode.REQUIRED, HttpStatus.BAD_REQUEST);
        }
        // If currentPassword is provided by anyone, validate it
        if (input.getCurrentPassword() != null && !input.getCurrentPassword().isBlank()) {
            if (!passwordEncoder.matches(input.getCurrentPassword(), caller.getPassword())) {
                throw new CustomException(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
            }
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        target.setPassword(passwordEncoder.encode(input.getNewPassword()));
        userRepository.save(target);
    }
}