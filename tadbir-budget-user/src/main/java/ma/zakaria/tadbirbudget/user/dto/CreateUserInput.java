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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import ma.zakaria.tadbirbudget.validation.ValidPassword;

import java.util.List;
import java.util.UUID;

@Data
public class CreateUserInput {

    @NotBlank
    @Size(max = 100)
    private String fullName;

    @NotBlank
    @Size(max = 20)
    private String cin;

    @NotBlank
    @Size(max = 20)
    private String phoneNumber;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 255)
    private String address;

    @NotBlank
    @ValidPassword
    private String password;

    /** Assignable roles: ROLE_ADMIN or ROLE_INSTRUCTOR only. Validated in service. */
    @NotEmpty
    private List<String> roles;

    /**
     * Optional: this user's direct superior (N+1) for the validation hierarchy. Must be an
     * existing user id. Leave null for users at the top of the chain.
     */
    private UUID managerId;
}