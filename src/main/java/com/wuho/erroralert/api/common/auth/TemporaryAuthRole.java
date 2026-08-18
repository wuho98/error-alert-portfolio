package com.wuho.erroralert.api.common.auth;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum TemporaryAuthRole {

    ADMIN,
    OPERATOR;

    public static Optional<TemporaryAuthRole> fromHeader(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalizedValue = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(role -> role.name().equals(normalizedValue))
                .findFirst();
    }

    // Phase 3에서 VIEWER 같은 하위 역할이 추가되면 이 메서드는 false를 반환해야 한다.
    public boolean canAccessOperatorApi() {
        return this == ADMIN || this == OPERATOR;
    }

    public boolean canAccessAdminApi() {
        return this == ADMIN;
    }
}
