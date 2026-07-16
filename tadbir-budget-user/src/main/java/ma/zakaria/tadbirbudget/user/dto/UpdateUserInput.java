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

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * PATCH payload — all fields are optional. Null means "leave unchanged".
 * If a field is provided it must satisfy its size constraint.
 *
 * password, and enabled are intentionally absent:
 * each has its own dedicated endpoint.
 *
 * roles and managerId are admin-only and silently ignored for self-updates.
 */
@Data
public class UpdateUserInput {

    @Email
    @Size(max = 255)
    private String email;

    @Size(min = 1, max = 100)
    private String fullName;

    @Size(min = 1, max = 20)
    private String cin;

    @Size(min = 1, max = 20)
    private String phoneNumber;

    @Size(max = 255)
    private String address;

    private List<String> roles;

    /** Admin-only: this user's direct superior (N+1). Must be an existing, different user. */
    private UUID managerId;
}
