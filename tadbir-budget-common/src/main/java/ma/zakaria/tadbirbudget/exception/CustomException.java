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

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Domain-level exception that carries a typed {@link ErrorCode}.
 * Throw this instead of {@link org.springframework.web.server.ResponseStatusException}
 * so the global handler can return a consistent, code-based error body.
 */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;

    public CustomException(ErrorCode errorCode, HttpStatus status) {
        super(errorCode.name());
        this.errorCode = errorCode;
        this.status    = status;
    }
}