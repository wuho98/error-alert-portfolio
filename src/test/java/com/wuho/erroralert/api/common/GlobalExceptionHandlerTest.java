package com.wuho.erroralert.api.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void successResponseIsWrappedWithData() throws Exception {
        mockMvc.perform(post("/test/api-common/success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "payment-service"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("payment-service"));
    }

    @Test
    void validationFailureReturnsApiErrorResponse() throws Exception {
        mockMvc.perform(post("/test/api-common/success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"))
                .andExpect(jsonPath("$.message").value("입력값 검증에 실패했습니다."));
    }

    @Test
    void malformedJsonReturnsInvalidRequest() throws Exception {
        mockMvc.perform(post("/test/api-common/success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."));
    }

    @Test
    void customUnauthorizedExceptionReturns401() throws Exception {
        mockMvc.perform(get("/test/api-common/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

    @Test
    void customForbiddenExceptionReturns403() throws Exception {
        mockMvc.perform(get("/test/api-common/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"))
                .andExpect(jsonPath("$.message").value("권한이 부족합니다."));
    }

    @Test
    void customNotFoundExceptionReturns404() throws Exception {
        mockMvc.perform(get("/test/api-common/project-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P001"))
                .andExpect(jsonPath("$.message").value("프로젝트를 찾을 수 없습니다."));
    }

    @Test
    void unknownPathReturnsCommonNotFound() throws Exception {
        mockMvc.perform(get("/test/api-common/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("C003"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));
    }

    @Test
    void customConflictExceptionReturns409() throws Exception {
        mockMvc.perform(get("/test/api-common/project-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("P002"))
                .andExpect(jsonPath("$.message").value("프로젝트 상태가 충돌했습니다."));
    }

    @Test
    void responseStatusNotFoundReturnsCommonNotFound() throws Exception {
        mockMvc.perform(get("/test/api-common/response-status-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("C003"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));
    }

    @Test
    void responseStatusBadRequestReturnsInvalidRequest() throws Exception {
        mockMvc.perform(get("/test/api-common/response-status-bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."));
    }

    @Test
    void unexpectedExceptionReturns500WithoutInternalMessage() throws Exception {
        mockMvc.perform(get("/test/api-common/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("C999"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }

    @Test
    void dependencyFailureReturns503WithCommonCode() throws Exception {
        mockMvc.perform(get("/test/api-common/dependency-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("C004"))
                .andExpect(jsonPath("$.message").value("일시적으로 서비스를 이용할 수 없습니다."));
    }

    @Test
    void transactionFailureReturns503WithCommonCode() throws Exception {
        mockMvc.perform(get("/test/api-common/transaction-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("C004"))
                .andExpect(jsonPath("$.message").value("일시적으로 서비스를 이용할 수 없습니다."));
    }

    @RestController
    @RequestMapping("/test/api-common")
    public static class TestController {

        @PostMapping("/success")
        ApiResponse<TestResponse> success(@Valid @RequestBody TestRequest request) {
            return ApiResponse.of(new TestResponse(request.name()));
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED);
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new ApiException(ApiErrorCode.FORBIDDEN);
        }

        @GetMapping("/project-not-found")
        void projectNotFound() {
            throw new ApiException(ApiErrorCode.PROJECT_NOT_FOUND);
        }

        @GetMapping("/project-conflict")
        void projectConflict() {
            throw new ApiException(ApiErrorCode.PROJECT_CONFLICT);
        }

        @GetMapping("/response-status-not-found")
        void responseStatusNotFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found detail");
        }

        @GetMapping("/response-status-bad-request")
        void responseStatusBadRequest() {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad request detail");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("database password leaked by mistake");
        }

        @GetMapping("/dependency-failure")
        void dependencyFailure() {
            throw new DataAccessResourceFailureException("db connection failed");
        }

        @GetMapping("/transaction-failure")
        void transactionFailure() {
            throw new CannotCreateTransactionException("cannot open transaction");
        }
    }

    record TestRequest(@NotBlank String name) {
    }

    record TestResponse(String name) {
    }
}
