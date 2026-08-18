package com.wuho.erroralert.domain;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public enum ErrorCode {

    PAYMENT_PG_TIMEOUT("PAYMENT.PG_TIMEOUT"),
    PAYMENT_PG_SERVER_ERROR("PAYMENT.PG_SERVER_ERROR"),
    PAYMENT_APPROVAL_PROCESSING_FAILED("PAYMENT.APPROVAL_PROCESSING_FAILED"),
    PAYMENT_WEBHOOK_PROCESSING_FAILED("PAYMENT.WEBHOOK_PROCESSING_FAILED");

    private static final Map<String, ErrorCode> CODE_LOOKUP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ErrorCode::getCode, Function.identity()));

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public static ErrorCode fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Unsupported errorCode: null");
        }

        ErrorCode errorCode = CODE_LOOKUP.get(code);
        if (errorCode != null) {
            return errorCode;
        }
        throw new IllegalArgumentException("Unsupported errorCode: " + code);
    }

    public static boolean existsByCode(String code) {
        return CODE_LOOKUP.containsKey(code);
    }
}
