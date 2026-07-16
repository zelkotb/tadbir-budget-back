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
package ma.zakaria.tadbirbudget.auth.dto;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * Carries per-request metadata (client IP, User-Agent) extracted in the controller
 * so the service layer stays free of the Servlet API.
 */
public record AuthRequestContext(String ipAddress, String userAgent) {

    public static AuthRequestContext from(HttpServletRequest request) {
        return new AuthRequestContext(
                extractClientIp(request),
                truncate(request.getHeader("User-Agent"), 512)
        );
    }

    private static String extractClientIp(HttpServletRequest request) {
        for (String header : List.of("X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP")) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) return null;
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}