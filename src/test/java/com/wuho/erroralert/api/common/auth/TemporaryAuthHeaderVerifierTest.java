package com.wuho.erroralert.api.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TemporaryAuthHeaderVerifierTest {

    private final TemporaryAuthHeaderVerifier verifier = new TemporaryAuthHeaderVerifier();

    @Test
    void operatorRoleIsAllowed() {
        TemporaryAuthUserContext context = verifier.verifyOperatorOrHigher("3", "OPERATOR");

        assertThat(context.userId()).isEqualTo("3");
        assertThat(context.role()).isEqualTo(TemporaryAuthRole.OPERATOR);
    }

    @Test
    void adminRoleIsAllowed() {
        TemporaryAuthUserContext context = verifier.verifyOperatorOrHigher("1", "ADMIN");

        assertThat(context.userId()).isEqualTo("1");
        assertThat(context.role()).isEqualTo(TemporaryAuthRole.ADMIN);
    }

    @Test
    void headerValuesAreTrimmed() {
        TemporaryAuthUserContext context = verifier.verifyOperatorOrHigher(" 3 ", " OPERATOR ");

        assertThat(context.userId()).isEqualTo("3");
        assertThat(context.role()).isEqualTo(TemporaryAuthRole.OPERATOR);
    }

    @Test
    void mixedCaseRoleIsNormalized() {
        TemporaryAuthUserContext context = verifier.verifyOperatorOrHigher("1", " aDmIn ");

        assertThat(context.userId()).isEqualTo("1");
        assertThat(context.role()).isEqualTo(TemporaryAuthRole.ADMIN);
    }

    @Test
    void missingUserIdReturnsUnauthorized() {
        ApiException exception = catchApiException(
                () -> verifier.verifyOperatorOrHigher(null, "OPERATOR"));

        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.UNAUTHORIZED);
    }

    @Test
    void blankUserIdReturnsUnauthorized() {
        ApiException exception = catchApiException(
                () -> verifier.verifyOperatorOrHigher(" ", "OPERATOR"));

        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.UNAUTHORIZED);
    }

    @Test
    void missingRoleReturnsUnauthorized() {
        ApiException exception = catchApiException(
                () -> verifier.verifyOperatorOrHigher("3", null));

        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.UNAUTHORIZED);
    }

    @Test
    void blankRoleReturnsUnauthorized() {
        ApiException exception = catchApiException(
                () -> verifier.verifyOperatorOrHigher("3", " "));

        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.UNAUTHORIZED);
    }

    @Test
    void unsupportedRoleReturnsForbidden() {
        ApiException exception = catchApiException(
                () -> verifier.verifyOperatorOrHigher("3", "GUEST"));

        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.FORBIDDEN);
    }

    @Test
    void lowercaseRoleIsNormalized() {
        TemporaryAuthUserContext context = verifier.verifyOperatorOrHigher("3", "operator");

        assertThat(context.userId()).isEqualTo("3");
        assertThat(context.role()).isEqualTo(TemporaryAuthRole.OPERATOR);
    }

    @Test
    void adminRoleIsAllowedForAdminApi() {
        TemporaryAuthUserContext context = verifier.verifyAdmin("1", "ADMIN");

        assertThat(context.userId()).isEqualTo("1");
        assertThat(context.role()).isEqualTo(TemporaryAuthRole.ADMIN);
    }

    @Test
    void adminRoleHeaderIsNormalizedForAdminApi() {
        TemporaryAuthUserContext context = verifier.verifyAdmin(" 1 ", " aDmIn ");

        assertThat(context.userId()).isEqualTo("1");
        assertThat(context.role()).isEqualTo(TemporaryAuthRole.ADMIN);
    }

    @Test
    void operatorRoleReturnsForbiddenForAdminApi() {
        ApiException exception = catchApiException(
                () -> verifier.verifyAdmin("3", "OPERATOR"));

        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.FORBIDDEN);
    }

    @Test
    void unsupportedRoleReturnsForbiddenForAdminApi() {
        ApiException exception = catchApiException(
                () -> verifier.verifyAdmin("3", "GUEST"));

        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.FORBIDDEN);
    }

    @Test
    void requestHeadersReturnContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TemporaryAuthHeaderVerifier.USER_ID_HEADER, "3");
        request.addHeader(TemporaryAuthHeaderVerifier.ROLE_HEADER, "OPERATOR");

        TemporaryAuthUserContext context = verifier.verifyOperatorOrHigher(request);

        assertThat(context.userId()).isEqualTo("3");
        assertThat(context.role()).isEqualTo(TemporaryAuthRole.OPERATOR);
    }

    @Test
    void nullRequestReturnsKoreanGuardMessage() {
        assertThatThrownBy(() -> verifier.verifyOperatorOrHigher((HttpServletRequest) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request는 null일 수 없습니다.");
    }

    private ApiException catchApiException(ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);
        assertThat(thrown).isInstanceOf(ApiException.class);
        return (ApiException) thrown;
    }
}
