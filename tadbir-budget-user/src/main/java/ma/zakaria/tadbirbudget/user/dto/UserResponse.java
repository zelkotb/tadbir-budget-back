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
package ma.zakaria.tadbirbudget.user.dto;

import ma.zakaria.tadbirbudget.entity.OrgUnit;
import ma.zakaria.tadbirbudget.entity.User;

import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID            id,
        String          uid,
        String          fullName,
        String          email,
        String          phoneNumber,
        List<String>    roles,
        boolean         enabled,
        int             failedLoginAttempts,
        List<String>    permissions,
        UUID            managerId,
        String          managerUid,
        String          managerFullName,
        UUID            orgUnitId,
        String          orgUnitName
) {
    /** Without the manager / org unit resolved (ids only). */
    public static UserResponse from(User user) {
        return from(user, null, null);
    }

    /** With the manager entity resolved so the UI can show the manager's name. */
    public static UserResponse from(User user, User manager) {
        return from(user, manager, null);
    }

    /** With the manager and the org-unit resolved so the UI can show both names. */
    public static UserResponse from(User user, User manager, OrgUnit orgUnit) {
        return new UserResponse(
                user.getId(),
                user.getUid(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRoles(),
                user.isEnabled(),
                user.getFailedLoginAttempts(),
                user.getPermissions(),
                user.getManagerId(),
                manager == null ? null : manager.getUid(),
                manager == null ? null : manager.getFullName(),
                user.getOrgUnitId(),
                orgUnit == null ? null : orgUnit.getName()
        );
    }
}
