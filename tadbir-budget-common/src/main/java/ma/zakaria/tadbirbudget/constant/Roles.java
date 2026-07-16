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

    public final String ADMIN             = "ROLE_ADMIN";
    public final String USER              = "ROLE_USER";
    public final String INSTRUCTOR        = "ROLE_INSTRUCTOR";
    /** A hierarchical validator (the instructor's N+1, then N+2, …). Generic — any number of steps. */
    public final String VALIDATOR         = "ROLE_VALIDATOR";
    /** The commission's decision-maker (one user represents the commission). */
    public final String COMMISSION        = "ROLE_COMMISSION";
    /** A member of the commission. */
    public final String MEMBRE_COMMISSION = "ROLE_MEMBRE_COMMISSION";
    /** Top management: read-only oversight — sees everything, changes/validates nothing. */
    public final String MANAGEMENT        = "ROLE_MANAGEMENT";

    // ── @PreAuthorize SpEL expressions ────────────────────────────────────────

    public final String IS_ADMIN             = "hasRole('ADMIN')";
    public final String IS_USER              = "hasRole('USER')";
    public final String IS_INSTRUCTOR        = "hasRole('INSTRUCTOR')";
    public final String IS_VALIDATOR         = "hasRole('VALIDATOR')";
    public final String IS_COMMISSION        = "hasRole('COMMISSION')";
    public final String IS_MEMBRE_COMMISSION = "hasRole('MEMBRE_COMMISSION')";
    public final String IS_MANAGEMENT        = "hasRole('MANAGEMENT')";

    public final String IS_ADMIN_OR_INSTRUCTOR = "hasAnyRole('ADMIN', 'INSTRUCTOR')";

    /**
     * <b>Write</b> actors on the instruction workflow: instructor, hierarchical validators
     * (N+1, N+2, …), commission and its members, and admins. (Management is excluded — it cannot
     * act.) Adding a new validation role to the BPMN only means adding it here.
     */
    public final String IS_WORKFLOW_ACTOR =
            "hasAnyRole('ADMIN', 'INSTRUCTOR', 'VALIDATOR', 'COMMISSION', 'MEMBRE_COMMISSION')";

    /** <b>Read</b> access to the workflow/requests: every actor plus read-only MANAGEMENT. */
    public final String IS_WORKFLOW_VIEWER =
            "hasAnyRole('ADMIN', 'INSTRUCTOR', 'VALIDATOR', 'COMMISSION', 'MEMBRE_COMMISSION', 'MANAGEMENT')";

    /** People/HR-style analytics (per-employee comparisons): admins and management only. */
    public final String IS_PEOPLE_ANALYST = "hasAnyRole('ADMIN', 'MANAGEMENT')";

    /** Write-capable workflow actor (excludes management). */
    public boolean isWorkflowActor(java.util.Collection<String> roles) {
        return roles != null && (roles.contains(ADMIN) || roles.contains(INSTRUCTOR)
                || roles.contains(VALIDATOR) || roles.contains(COMMISSION)
                || roles.contains(MEMBRE_COMMISSION));
    }

    /** Read access to the workflow/requests: any actor or read-only management. */
    public boolean canViewWorkflow(java.util.Collection<String> roles) {
        return roles != null && (isWorkflowActor(roles) || roles.contains(MANAGEMENT));
    }
}