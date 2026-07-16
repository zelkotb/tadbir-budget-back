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
package ma.zakaria.tadbirbudget.admin;

import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.constant.Roles;
import ma.zakaria.tadbirbudget.entity.User;
import ma.zakaria.tadbirbudget.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin API for the global change-freeze (maintenance) switch. */
@RestController
@RequestMapping("/api/v1/admin/change-freeze")
@RequiredArgsConstructor
@PreAuthorize(Roles.IS_ADMIN)
public class ChangeFreezeController {

    private final ChangeFreezeService changeFreezeService;

    /** GET /api/v1/admin/change-freeze — current freeze state. */
    @GetMapping
    public ResponseEntity<ChangeFreezeOutput> changeFreeze() {
        return ResponseEntity.ok(changeFreezeService.current());
    }

    /** PUT /api/v1/admin/change-freeze?frozen=true|false — enable/disable the freeze. */
    @PutMapping
    public ResponseEntity<ChangeFreezeOutput> setChangeFreeze(@RequestParam boolean frozen) {
        return ResponseEntity.ok(changeFreezeService.setFrozen(frozen, currentUserId()));
    }

    private UUID currentUserId() {
        return ((User) SecurityUtils.getCurrentUser()).getId();
    }
}
