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
package ma.zakaria.tadbirbudget.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.constant.Roles;
import ma.zakaria.tadbirbudget.enums.AuditAction;
import ma.zakaria.tadbirbudget.user.dto.*;
import ma.zakaria.tadbirbudget.user.service.UserAuditService;
import ma.zakaria.tadbirbudget.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService      userService;
    private final UserAuditService userAuditService;

    /**
     * GET /api/v1/user
     * Paginated user list. All filters are optional and combinable.
     * fullName, email, cin support partial case-insensitive matching.
     * enabled is an exact match.
     */
    @GetMapping
    @PreAuthorize(Roles.IS_ADMIN)
    public ResponseEntity<Page<UserResponse>> listUsers(
            @RequestParam(required = false) String          fullName,
            @RequestParam(required = false) String          email,
            @RequestParam(required = false) String          cin,
            @RequestParam(required = false) Boolean         enabled,
            @RequestParam(required = false) List<String>    roles,
            @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return ResponseEntity.ok(
                userService.listUsers(fullName, email, cin, enabled, roles, pageable));
    }

    /**
     * POST /api/v1/user
     * Creates a user and assigns roles. Assignable roles: ROLE_ADMIN, ROLE_INSTRUCTOR.
     */
    @PostMapping
    @PreAuthorize(Roles.IS_ADMIN)
    public ResponseEntity<Void> createUser(@Valid @RequestBody CreateUserInput input) {
        userService.createUser(input);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * GET /api/v1/user/me
     * Returns the currently authenticated user's own profile.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe() {
        return ResponseEntity.ok(userService.getMe());
    }

    /**
     * GET /api/v1/user/staff
     * All staff users (any role other than ROLE_USER) — used to populate the manager (N+1) picker.
     */
    @GetMapping("/staff")
    @PreAuthorize(Roles.IS_ADMIN)
    public ResponseEntity<List<UserResponse>> listStaff() {
        return ResponseEntity.ok(userService.listStaff());
    }

    /**
     * GET /api/v1/user/{id}
     * Admin: any user. Authenticated user: own profile only.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUser(id));
    }

    /**
     * PATCH /api/v1/user/{id}
     * Admin: any user, including roles. Authenticated user: own profile only, roles ignored.
     * Only non-null fields are applied. password, enabled have dedicated endpoints.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateUserInput input) {
        return ResponseEntity.ok(userService.updateUser(id, input));
    }

    /**
     * PUT /api/v1/user/{id}/enable
     * Re-enables a disabled account and resets the failed-attempt counter.
     * Required when an account was disabled after repeated failed login attempts.
     */
    @PutMapping("/{id}/enable")
    @PreAuthorize(Roles.IS_ADMIN)
    public ResponseEntity<Void> enableUser(@PathVariable UUID id) {
        userService.enableUser(id);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /api/v1/user/{id}/disable
     * Disables an account. Admin cannot disable their own account.
     */
    @PutMapping("/{id}/disable")
    @PreAuthorize(Roles.IS_ADMIN)
    public ResponseEntity<Void> disableUser(@PathVariable UUID id) {
        userService.disableUser(id);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /api/v1/user/{id}/password
     * Admin can change any user's password without providing the current password.
     * A user changing their own password must provide currentPassword.
     */
    @PutMapping("/{id}/password")
    public ResponseEntity<Void> changePassword(@PathVariable UUID id,
                                               @Valid @RequestBody ChangePasswordInput input) {
        userService.changePassword(id, input);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/v1/user/audit/{revisionId}/diff
     * Field-level diff for a single audit revision.
     * revisionId comes from any UserAuditResponse.revisionId.
     */
    @GetMapping("/audit/{revisionId}/diff")
    @PreAuthorize(Roles.IS_ADMIN)
    public ResponseEntity<UserAuditDiffResponse> getAuditDiff(@PathVariable int revisionId) {
        return ResponseEntity.ok(userAuditService.getDiff(revisionId));
    }

    /**
     * GET /api/v1/user/audit
     * Paginated Envers audit log for User entity.
     *
     * @param performedBy partial email of who made the change
     * @param ip          partial IP address
     * @param action      CREATE / UPDATE / DELETE
     * @param userId      exact user whose history to retrieve
     * @param date        DD/MM/YYYY — filter by specific day
     */
    @GetMapping("/audit")
    @PreAuthorize(Roles.IS_ADMIN)
    public ResponseEntity<Page<UserAuditResponse>> getAudit(
            @RequestParam(required = false) String      performedBy,
            @RequestParam(required = false) String      ip,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) UUID        userId,
            @RequestParam(required = false) String      date,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(
                userAuditService.query(performedBy, ip, action, userId, date, pageable));
    }
}