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
import ma.zakaria.tadbirbudget.constant.Roles;
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
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    /** Roles an admin may assign when creating/updating an account. */
    private static final Set<String> ASSIGNABLE_ROLES = Set.of(
            Roles.ADMIN, Roles.EMPLOYEE, Roles.CELL_MANAGER, Roles.SERVICE_MANAGER, Roles.DEPARTMENT_MANAGER,
            Roles.DIRECTION_MANAGER, Roles.POLE_MANAGER, Roles.DIRECTION_GENERALE, Roles.CONTROLE_GESTION);

    private final UserRepository    userRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final PasswordEncoder   passwordEncoder;

    @Transactional(readOnly = true)
    public UserResponse getMe() {
        User caller = (User) SecurityUtils.getCurrentUser();
        return userRepository.findById(caller.getId())
                .map(UserResponse::from)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        User caller = (User) SecurityUtils.getCurrentUser();
        if (!caller.getRoles().contains(Roles.ADMIN) && !caller.getId().equals(id)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
        return userRepository.findById(id)
                .map(UserResponse::from)
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
                                        Boolean enabled,
                                        List<String> roles, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (fullName        != null) predicates.add(cb.like(cb.lower(root.get("fullName")),
                                                                "%" + fullName.toLowerCase() + "%"));
            if (email           != null) predicates.add(cb.like(cb.lower(root.get("email")),
                                                                "%" + email.toLowerCase() + "%"));
            if (uid             != null) predicates.add(cb.like(cb.lower(root.get("uid")),
                                                                "%" + uid.toLowerCase() + "%"));
            if (enabled         != null) predicates.add(cb.equal(root.get("enabled"), enabled));
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
        return userRepository.findAll(spec, pageable).map(UserResponse::from);
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
                .managerId(input.getManagerId())
                .orgUnitId(input.getOrgUnitId())
                .build());
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