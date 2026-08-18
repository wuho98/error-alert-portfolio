package com.wuho.erroralert.api.common.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TemporaryAuthUserContextTest {

    @Test
    void nullUserIdReturnsKoreanGuardMessage() {
        assertThatThrownBy(() -> new TemporaryAuthUserContext(null, TemporaryAuthRole.OPERATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId는 비어 있을 수 없습니다.");
    }

    @Test
    void blankUserIdReturnsKoreanGuardMessage() {
        assertThatThrownBy(() -> new TemporaryAuthUserContext(" ", TemporaryAuthRole.OPERATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId는 비어 있을 수 없습니다.");
    }

    @Test
    void nullRoleReturnsKoreanGuardMessage() {
        assertThatThrownBy(() -> new TemporaryAuthUserContext("3", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("role은 null일 수 없습니다.");
    }
}
