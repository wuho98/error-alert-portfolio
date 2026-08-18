package com.wuho.erroralert.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.wuho.erroralert.domain.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReceiveErrorEventRequestTest {

    private ValidatorFactory validatorFactory;
    private Validator validator;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @MethodSource("allowedErrorCodes")
    void acceptsAllowedErrorCode(String errorCode) {
        ReceiveErrorEventRequest request = new ReceiveErrorEventRequest(
                errorCode,
                "PG approval request timed out after 5000ms",
                Instant.parse("2026-08-04T12:34:45Z"));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void acceptsMessageLongerThanOneThousandCharacters() {
        ReceiveErrorEventRequest request = new ReceiveErrorEventRequest(
                ErrorCode.PAYMENT_PG_TIMEOUT.getCode(),
                "a".repeat(1001),
                Instant.parse("2026-08-04T12:34:45Z"));

        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PAYMENT.UNKNOWN",
            "PAYMENT.PG_TIMEOUT ",
            "payment.pg_timeout"
    })
    void rejectsUnsupportedErrorCode(String errorCode) {
        ReceiveErrorEventRequest request = new ReceiveErrorEventRequest(
                errorCode,
                "PG approval request timed out after 5000ms",
                Instant.parse("2026-08-04T12:34:45Z"));

        assertThat(violationPaths(request)).contains("errorCode");
    }

    @Test
    void rejectsBlankErrorCode() {
        ReceiveErrorEventRequest request = new ReceiveErrorEventRequest(
                " ",
                "PG approval request timed out after 5000ms",
                Instant.parse("2026-08-04T12:34:45Z"));

        Set<String> paths = violationPaths(request);
        assertThat(paths).contains("errorCode");
        assertThat(paths).hasSize(1);
    }

    @Test
    void rejectsMissingMessage() {
        ReceiveErrorEventRequest request = new ReceiveErrorEventRequest(
                ErrorCode.PAYMENT_PG_TIMEOUT.getCode(),
                null,
                Instant.parse("2026-08-04T12:34:45Z"));

        assertThat(violationPaths(request)).contains("message");
    }

    @Test
    void rejectsBlankMessage() {
        ReceiveErrorEventRequest request = new ReceiveErrorEventRequest(
                ErrorCode.PAYMENT_PG_TIMEOUT.getCode(),
                "",
                Instant.parse("2026-08-04T12:34:45Z"));

        assertThat(violationPaths(request)).contains("message");
    }

    @Test
    void rejectsMissingOccurredAt() {
        ReceiveErrorEventRequest request = new ReceiveErrorEventRequest(
                ErrorCode.PAYMENT_PG_TIMEOUT.getCode(),
                "PG approval request timed out after 5000ms",
                null);

        assertThat(violationPaths(request)).contains("occurredAt");
    }

    private Set<String> violationPaths(ReceiveErrorEventRequest request) {
        return validator.validate(request)
                .stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static Stream<String> allowedErrorCodes() {
        return Stream.of(ErrorCode.values())
                .map(ErrorCode::getCode);
    }
}
