package com.wuho.erroralert.api.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        ApiErrorCode errorCode = exception.getErrorCode();
        log.debug("API exception handled: code={}, type={}",
                errorCode.getCode(), exception.getClass().getSimpleName());
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiErrorResponse.of(errorCode, exception.getResponseMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiErrorResponse> handleValidationException(Exception exception) {
        log.debug("Validation failed: type={}, message={}",
                exception.getClass().getSimpleName(), exception.getMessage());
        return build(ApiErrorCode.VALIDATION_FAILED);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInvalidRequestException(Exception exception) {
        log.debug("Invalid API request: type={}, message={}",
                exception.getClass().getSimpleName(), exception.getMessage());
        return build(ApiErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler({
            NoHandlerFoundException.class,
            NoResourceFoundException.class
    })
    public ResponseEntity<ApiErrorResponse> handleNoResourceException(Exception exception) {
        log.debug("API resource not found: type={}, message={}",
                exception.getClass().getSimpleName(), exception.getMessage());
        return build(ApiErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException exception) {
        ApiErrorCode errorCode = mapResponseStatusException(exception.getStatusCode());
        log.debug("Response status exception handled: status={}, code={}, type={}",
                exception.getStatusCode().value(), errorCode.getCode(), exception.getClass().getSimpleName());
        return build(errorCode);
    }

    @ExceptionHandler({
            DataAccessResourceFailureException.class,
            CannotCreateTransactionException.class
    })
    public ResponseEntity<ApiErrorResponse> handleDependencyFailure(Exception exception) {
        log.error("Dependency service unavailable", exception);
        return build(ApiErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Unexpected API exception", exception);
        return build(ApiErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ApiErrorCode mapResponseStatusException(HttpStatusCode statusCode) {
        int status = statusCode.value();
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return ApiErrorCode.UNAUTHORIZED;
        }
        if (status == HttpStatus.FORBIDDEN.value()) {
            return ApiErrorCode.FORBIDDEN;
        }
        if (status == HttpStatus.NOT_FOUND.value()) {
            return ApiErrorCode.RESOURCE_NOT_FOUND;
        }
        if (statusCode.is4xxClientError()) {
            return ApiErrorCode.INVALID_REQUEST;
        }
        return ApiErrorCode.INTERNAL_SERVER_ERROR;
    }

    private ResponseEntity<ApiErrorResponse> build(ApiErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiErrorResponse.from(errorCode));
    }
}
