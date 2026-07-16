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
package ma.zakaria.tadbirbudget.util;

import lombok.experimental.UtilityClass;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

@UtilityClass
public class SecurityUtils {

    /**
     * Returns the currently authenticated user as {@link UserDetails}.
     * Cast to the concrete {@code User} entity when needed.
     *
     * @throws CustomException ACCESS_DENIED if no authenticated principal is present
     */
    public UserDetails getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails ud) {
            return ud;
        }
        throw new CustomException(ErrorCode.ACCESS_DENIED, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Shortcut for the most common case: getting the current user's email
     * without casting to the concrete entity.
     */
    public String getCurrentUserEmail() {
        return getCurrentUser().getUsername();
    }
}