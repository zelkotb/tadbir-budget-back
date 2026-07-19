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

@UtilityClass
public final class Roles {

    // ── Authority strings (stored in DB / GrantedAuthority) ───────────────────
    // Naming convention: no role name may be a
    // substring of another (the roles column is a CSV matched with LIKE — see UserRepository).

    public final String ADMIN               = "ROLE_ADMIN";
    public final String EMPLOYEE            = "ROLE_EMPLOYEE";
    public final String DEPARTMENT_MANAGER  = "ROLE_DEPARTMENT_MANAGER";
    public final String DIRECTION_MANAGER   = "ROLE_DIRECTION_MANAGER";
    public final String POLE_MANAGER        = "ROLE_POLE_MANAGER";
    public final String DIRECTION_GENERALE  = "ROLE_DIRECTION_GENERALE";
    public final String CONTROLE_GESTION    = "ROLE_CONTROLE_GESTION";

    // ── @PreAuthorize SpEL expressions ────────────────────────────────────────

    public final String IS_ADMIN               = "hasRole('ADMIN')";
    public final String IS_EMPLOYEE            = "hasRole('EMPLOYEE')";
    public final String IS_DEPARTMENT_MANAGER  = "hasRole('DEPARTMENT_MANAGER')";
    public final String IS_DIRECTION_MANAGER   = "hasRole('DIRECTION_MANAGER')";
    public final String IS_POLE_MANAGER        = "hasRole('POLE_MANAGER')";
    public final String IS_DIRECTION_GENERALE  = "hasRole('DIRECTION_GENERALE')";
    public final String IS_CONTROLE_GESTION    = "hasRole('CONTROLE_GESTION')";

    /**
     * Any authenticated business user may take part in a workflow — each BPMN decides, via its
     * task {@code candidateGroups}, which role acts on which step. This guard simply keeps the
     * workflow runtime API open to every real user role.
     */
    public final String IS_WORKFLOW_ACTOR = "hasAnyRole('ADMIN', 'EMPLOYEE', "
            + "'DEPARTMENT_MANAGER', 'DIRECTION_MANAGER', 'POLE_MANAGER', 'DIRECTION_GENERALE', "
            + "'CONTROLE_GESTION')";
}