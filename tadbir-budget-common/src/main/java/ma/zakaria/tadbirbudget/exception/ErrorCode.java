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
package ma.zakaria.tadbirbudget.exception;

/**
 * Machine-readable error codes returned in every error response.
 * The frontend maps these to localised user messages.
 */
public enum ErrorCode {

    // ── Authentication ───────────────────────────────────────────────────────
    EMAIL_ALREADY_EXISTS,
    UID_ALREADY_EXISTS,
    INVALID_CREDENTIALS,
    ACCOUNT_DISABLED,
    USER_NOT_FOUND,
    INVALID_ROLE,

    // ── Refresh token ────────────────────────────────────────────────────────
    REFRESH_TOKEN_MISSING,
    REFRESH_TOKEN_INVALID,
    REFRESH_TOKEN_REVOKED,
    REFRESH_TOKEN_EXPIRED,

    // ── Input validation — top-level ────────────────────────────────────────
    VALIDATION_ERROR,
    INVALID_REQUEST_BODY,

    // ── Input validation — per-field codes (used in fieldErrors map) ─────────
    REQUIRED,
    INVALID_EMAIL,
    INVALID_SIZE,
    OUT_OF_RANGE,
    INVALID_FORMAT,
    MUST_BE_POSITIVE,
    MUST_BE_NEGATIVE,
    MUST_BE_FUTURE,
    MUST_BE_PAST,
    INVALID_VALUE,

    // ── HTTP / Security ──────────────────────────────────────────────────────
    ACCESS_DENIED,
    RESOURCE_NOT_FOUND,
    METHOD_NOT_ALLOWED,
    RATE_LIMIT_EXCEEDED,
    WEAK_PASSWORD,
    /** Generic fallback for a DB constraint violation (FK still referenced, unique clash, …). */
    DATA_INTEGRITY_VIOLATION,

    // ── Workflow ─────────────────────────────────────────────────────────────
    PROCESS_NOT_FOUND,
    PROCESS_CODE_ALREADY_EXISTS,
    PROCESS_VERSION_NOT_FOUND,
    NO_PUBLISHED_VERSION,
    VERSION_NOT_EDITABLE,
    WORKFLOW_HAS_NO_STEPS,
    STEP_INSTANCE_NOT_FOUND,
    NOT_AN_APPROVER,
    INVALID_WORKFLOW_STATE,
    TASK_NOT_RESERVED,
    INVALID_REVIEW_STATUS,

    // ── Notification ─────────────────────────────────────────────────────────
    NOTIFICATION_NOT_FOUND,

    // ── Projects ─────────────────────────────────────────────────────────────
    PROJECT_NOT_FOUND,
    PROJECT_INVALID_STATUS,
    /** The project must be ACTIVE (started) for this action — e.g. starting a phase. */
    PROJECT_NOT_ACTIVE,
    /** The project cannot be terminated while some of its phases are still open (not TERMINATED). */
    PROJECT_HAS_OPEN_PHASES,
    /** The project's termination date is earlier than the latest phase end date. */
    PROJECT_TERMINATION_BEFORE_PHASE_END,
    PROJECT_DOCUMENT_NOT_FOUND,
    PROJECT_PHASE_NOT_FOUND,
    PROJECT_PHASE_INVALID_STATUS,
    PROJECT_PHASE_INVALID_DATES,
    /** A phase cannot start before its project's start date. */
    PROJECT_PHASE_START_BEFORE_PROJECT,
    PROJECT_PHASE_WEIGHT_EXCEEDED,

    // ── Organisation structure ───────────────────────────────────────────────
    ORG_UNIT_NOT_FOUND,
    ORG_UNIT_CYCLE,
    ORG_UNIT_HAS_CHILDREN,
    ORG_UNIT_HAS_USERS,

    // ── Budget — nomenclature definition (level template) ────────────────────
    NOMENCLATURE_DEFINITION_NOT_FOUND,
    NOMENCLATURE_DEFINITION_NAME_EXISTS,
    NOMENCLATURE_DEFINITION_NO_LEVELS,
    NOMENCLATURE_DEFINITION_LEVEL_DUPLICATE,
    NOMENCLATURE_DEFINITION_IN_USE,

    // ── Budget — nomenclature (real tree) ────────────────────────────────────
    NOMENCLATURE_NOT_FOUND,
    NOMENCLATURE_NAME_EXISTS,
    NOMENCLATURE_NOT_DRAFT,
    NOMENCLATURE_EMPTY,
    RUBRIQUE_NOT_FOUND,
    RUBRIQUE_CODE_EXISTS,
    RUBRIQUE_PARENT_IS_LEAF,
    RUBRIQUE_HAS_CHILDREN,
    RUBRIQUE_WRONG_NOMENCLATURE,
    NOMENCLATURE_NOT_FIXED,
    NOMENCLATURE_ARCHIVED,
    RUBRIQUE_HAS_ASSIGNMENTS,
    RUBRIQUE_ASSIGNMENT_EXISTS,
    RUBRIQUE_ASSIGNMENT_NOT_FOUND,

    // ── Files ────────────────────────────────────────────────────────────────
    FILE_EMPTY,
    FILE_TOO_LARGE,
    FILE_TYPE_NOT_ALLOWED,
    FILE_NOT_FOUND,
    INVALID_FILE_PATH,
    FILE_STORAGE_ERROR,

    // ── Settings (paramétrage) ───────────────────────────────────────────────
    SETTING_NOT_FOUND,
    SETTING_INVALID_VALUE,

    // ── Generic ──────────────────────────────────────────────────────────────
    INTERNAL_ERROR
}