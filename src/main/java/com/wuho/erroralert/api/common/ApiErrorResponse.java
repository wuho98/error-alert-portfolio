package com.wuho.erroralert.api.common;

public record ApiErrorResponse(String code, String message) {

    public static ApiErrorResponse from(ApiErrorCode errorCode) {
        return new ApiErrorResponse(errorCode.getCode(), errorCode.getMessage());
    }

    public static ApiErrorResponse of(ApiErrorCode errorCode, String message) {
        return new ApiErrorResponse(errorCode.getCode(), message);
    }
}
