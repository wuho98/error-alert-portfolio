package com.wuho.erroralert.api.common.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TemporaryAuthRoleTest {

    @Test
    void fromHeaderNormalizesHeaderValue() {
        assertThat(TemporaryAuthRole.fromHeader(" operator "))
                .contains(TemporaryAuthRole.OPERATOR);
        assertThat(TemporaryAuthRole.fromHeader("aDmIn"))
                .contains(TemporaryAuthRole.ADMIN);
    }

    @Test
    void fromHeaderReturnsEmptyForMissingOrUnsupportedValue() {
        assertThat(TemporaryAuthRole.fromHeader(null)).isEmpty();
        assertThat(TemporaryAuthRole.fromHeader(" ")).isEmpty();
        assertThat(TemporaryAuthRole.fromHeader("GUEST")).isEmpty();
    }
}
