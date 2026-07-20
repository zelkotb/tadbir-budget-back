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

import ma.zakaria.tadbirbudget.entity.OrgUnit;

import java.util.UUID;

public record OrgUnitResponse(
        UUID    id,
        String  name,
        String  kind,
        UUID    parentId,
        UUID    managerId,
        String  managerFullName,
        String  path,
        int     depth,
        boolean active
) {
    public static OrgUnitResponse from(OrgUnit unit, String managerFullName) {
        return new OrgUnitResponse(
                unit.getId(),
                unit.getName(),
                unit.getKind(),
                unit.getParentId(),
                unit.getManagerId(),
                managerFullName,
                unit.getPath(),
                unit.getDepth(),
                unit.isActive()
        );
    }
}
