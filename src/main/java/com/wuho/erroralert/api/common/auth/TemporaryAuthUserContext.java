package com.wuho.erroralert.api.common.auth;

import java.util.Objects;

public record TemporaryAuthUserContext(String userId, TemporaryAuthRole role) {

    public TemporaryAuthUserContext {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId는 비어 있을 수 없습니다.");
        }
        Objects.requireNonNull(role, "role은 null일 수 없습니다.");
    }
}
