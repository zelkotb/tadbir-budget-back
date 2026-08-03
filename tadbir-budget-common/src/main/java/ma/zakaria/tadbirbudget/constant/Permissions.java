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
package ma.zakaria.tadbirbudget.constant;

import lombok.experimental.UtilityClass;

/**
 * Fine-grained permissions — an admin grants these to any user, independent of roles. They are carried
 * as Spring Security authorities (alongside the {@code ROLE_*} authorities) so {@code @PreAuthorize}
 * can check them with {@code hasAuthority(...)}. Maintained here as a catalogue, just like
 * {@link Roles}. Unlike roles there is no prefix: {@code hasRole('X')} forces a {@code ROLE_} prefix,
 * but {@code hasAuthority('Y')} matches the string as-is — so a permission is just its plain name.
 */
@UtilityClass
public final class Permissions {

    // ── Authority strings (stored in the users.permissions CSV / GrantedAuthority) ──

    /** Manage the budget nomenclature <b>definitions</b> (level templates). */
    public final String BUDGET_DEFINITION   = "BUDGET_DEFINITION";
    /** Manage the budget <b>nomenclatures</b> (real trees, rubriques, org-unit assignments). */
    public final String BUDGET_NOMENCLATURE = "BUDGET_NOMENCLATURE";

    // ── @PreAuthorize SpEL expressions ──────────────────────────────────────────
    // Admin and contrôle de gestion always manage the budget; otherwise the holder of the permission.

    public final String CAN_MANAGE_BUDGET_DEFINITION =
            "hasRole('ADMIN') or hasRole('CONTROLE_GESTION') or hasAuthority('BUDGET_DEFINITION')";
    public final String CAN_MANAGE_BUDGET_NOMENCLATURE =
            "hasRole('ADMIN') or hasRole('CONTROLE_GESTION') or hasAuthority('BUDGET_NOMENCLATURE')";
}
