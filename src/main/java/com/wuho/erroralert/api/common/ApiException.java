package com.wuho.erroralert.api.common;

import java.util.Objects;

public class ApiException extends RuntimeException {

    private final ApiErrorCode errorCode;
    private final String responseMessage;

    public ApiException(ApiErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public ApiException(ApiErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.responseMessage = hasText(message) ? message : errorCode.getMessage();
    }

    public ApiErrorCode getErrorCode() {
        return errorCode;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
