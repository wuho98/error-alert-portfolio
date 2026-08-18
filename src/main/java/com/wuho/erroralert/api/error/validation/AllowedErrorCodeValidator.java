package com.wuho.erroralert.api.error.validation;

import com.wuho.erroralert.domain.ErrorCode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AllowedErrorCodeValidator implements ConstraintValidator<AllowedErrorCode, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 필수값 검증은 @NotBlank에 맡긴다.
        if (value == null || value.isBlank()) {
            return true;
        }

        return ErrorCode.existsByCode(value);
    }
}
