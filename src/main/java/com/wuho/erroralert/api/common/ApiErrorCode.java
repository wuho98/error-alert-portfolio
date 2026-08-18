package com.wuho.erroralert.api.common;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "C001", "요청 값이 올바르지 않습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "C002", "입력값 검증에 실패했습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C003", "요청한 리소스를 찾을 수 없습니다."),
    DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "C004", "일시적으로 서비스를 이용할 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C999", "서버 내부 오류가 발생했습니다."),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "A002", "권한이 부족합니다."),

    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "프로젝트를 찾을 수 없습니다."),
    PROJECT_CONFLICT(HttpStatus.CONFLICT, "P002", "프로젝트 상태가 충돌했습니다."),

    ERROR_EVENT_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "EE001", "오류 이벤트 요청 값이 올바르지 않습니다."),

    ALERT_NOT_FOUND(HttpStatus.NOT_FOUND, "AL001", "알림을 찾을 수 없습니다."),

    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI001", "AI 응답을 받을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ApiErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
