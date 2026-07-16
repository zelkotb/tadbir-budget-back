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

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Unified error response body.
 *
 * <pre>
 * {
 *   "code":        "EMAIL_ALREADY_EXISTS",   // always present — frontend maps this to i18n message
 *   "status":      400,                       // mirrors the HTTP status code
 *   "timestamp":   "2026-05-26T10:00:00Z",
 *   "fieldErrors": {                          // only present for VALIDATION_ERROR
 *     "email":    "INVALID_EMAIL",
 *     "fullName": "REQUIRED"
 *   }
 * }
 * </pre>
 */
@Getter
@Builder
public class ApiError {

    private final ErrorCode code;
    private final int       status;
    private final String    timestamp;

    /** Field-level error codes — non-null only for {@link ErrorCode#VALIDATION_ERROR}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Map<String, ErrorCode> fieldErrors;

    public static ApiError of(ErrorCode code, int status) {
        return ApiError.builder()
                .code(code)
                .status(status)
                .timestamp(Instant.now().toString())
                .build();
    }
}