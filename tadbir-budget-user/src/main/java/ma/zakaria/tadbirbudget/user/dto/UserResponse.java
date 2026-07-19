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
        UUID            managerId
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUid(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRoles(),
                user.isEnabled(),
                user.getFailedLoginAttempts(),
                user.getManagerId()
        );
    }
}
