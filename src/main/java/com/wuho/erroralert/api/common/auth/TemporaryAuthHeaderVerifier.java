package com.wuho.erroralert.api.common.auth;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class TemporaryAuthHeaderVerifier {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String ROLE_HEADER = "X-Role";

    public TemporaryAuthUserContext verifyOperatorOrHigher(HttpServletRequest request) {
        Objects.requireNonNull(request, "request는 null일 수 없습니다.");
        return verifyOperatorOrHigher(
                request.getHeader(USER_ID_HEADER),
                request.getHeader(ROLE_HEADER));
    }

    public TemporaryAuthUserContext verifyOperatorOrHigher(String userId, String role) {
        TemporaryAuthUserContext context = verifyRole(userId, role);

        if (!context.role().canAccessOperatorApi()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN);
        }

        return context;
    }

    public TemporaryAuthUserContext verifyAdmin(HttpServletRequest request) {
        Objects.requireNonNull(request, "request는 null일 수 없습니다.");
        return verifyAdmin(
                request.getHeader(USER_ID_HEADER),
                request.getHeader(ROLE_HEADER));
    }

    public TemporaryAuthUserContext verifyAdmin(String userId, String role) {
        TemporaryAuthUserContext context = verifyRole(userId, role);

        if (!context.role().canAccessAdminApi()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN);
        }

        return context;
    }

    private TemporaryAuthUserContext verifyRole(String userId, String role) {
        String normalizedUserId = requireHeader(userId);
        String requiredRole = requireHeader(role);
        TemporaryAuthRole temporaryAuthRole = TemporaryAuthRole.fromHeader(requiredRole)
                .orElseThrow(() -> new ApiException(ApiErrorCode.FORBIDDEN));

        return new TemporaryAuthUserContext(normalizedUserId, temporaryAuthRole);
    }

    private String requireHeader(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED);
        }
        return value.trim();
    }
}
