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
package ma.zakaria.tadbirbudget.organisation.dto;

import ma.zakaria.tadbirbudget.entity.User;

import java.util.List;
import java.util.UUID;

/** A member of an org unit, as listed under {@code GET /api/v1/org-units/{id}/users}. */
public record OrgUnitUserResponse(
        UUID         id,
        String       uid,
        String       fullName,
        String       email,
        List<String> roles,
        UUID         orgUnitId
) {
    public static OrgUnitUserResponse from(User user) {
        return new OrgUnitUserResponse(
                user.getId(),
                user.getUid(),
                user.getFullName(),
                user.getEmail(),
                user.getRoles(),
                user.getOrgUnitId()
        );
    }
}
