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
package ma.zakaria.tadbirbudget.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;
    private static final String SPECIAL_CHARS = "!@#$%^&*()_+\\-=\\[\\]{}|;':\",./<>?";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) return false;

        boolean hasUpper   = value.chars().anyMatch(Character::isUpperCase);
        boolean hasDigit   = value.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = value.chars().anyMatch(c -> SPECIAL_CHARS.indexOf(c) >= 0);
        boolean noSpace    = value.chars().noneMatch(Character::isWhitespace);

        return hasUpper && hasDigit && hasSpecial && noSpace;
    }
}
