package com.wuho.erroralert.api.common.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wuho.erroralert.api.common.ApiResponse;
import com.wuho.erroralert.api.common.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class TemporaryAuthHeaderVerifierWebMvcTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController(new TemporaryAuthHeaderVerifier()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void operatorRoleIsAllowed() throws Exception {
        mockMvc.perform(get("/test/temporary-auth/operator")
                        .header(TemporaryAuthHeaderVerifier.USER_ID_HEADER, "3")
                        .header(TemporaryAuthHeaderVerifier.ROLE_HEADER, "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("3"))
                .andExpect(jsonPath("$.data.role").value("OPERATOR"));
    }

    @Test
    void adminRoleIsAllowed() throws Exception {
        mockMvc.perform(get("/test/temporary-auth/operator")
                        .header(TemporaryAuthHeaderVerifier.USER_ID_HEADER, "1")
                        .header(TemporaryAuthHeaderVerifier.ROLE_HEADER, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("1"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void roleHeaderIsNormalized() throws Exception {
        mockMvc.perform(get("/test/temporary-auth/operator")
                        .header(TemporaryAuthHeaderVerifier.USER_ID_HEADER, "3")
                        .header(TemporaryAuthHeaderVerifier.ROLE_HEADER, " operator "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("3"))
                .andExpect(jsonPath("$.data.role").value("OPERATOR"));
    }

    @Test
    void missingUserIdReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/test/temporary-auth/operator")
                        .header(TemporaryAuthHeaderVerifier.ROLE_HEADER, "OPERATOR"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    void missingRoleReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/test/temporary-auth/operator")
                        .header(TemporaryAuthHeaderVerifier.USER_ID_HEADER, "3"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    void unsupportedRoleReturnsForbidden() throws Exception {
        mockMvc.perform(get("/test/temporary-auth/operator")
                        .header(TemporaryAuthHeaderVerifier.USER_ID_HEADER, "3")
                        .header(TemporaryAuthHeaderVerifier.ROLE_HEADER, "GUEST"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    void adminRoleIsAllowedForAdminApi() throws Exception {
        mockMvc.perform(get("/test/temporary-auth/admin")
                        .header(TemporaryAuthHeaderVerifier.USER_ID_HEADER, "1")
                        .header(TemporaryAuthHeaderVerifier.ROLE_HEADER, "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("1"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void operatorRoleReturnsForbiddenForAdminApi() throws Exception {
        mockMvc.perform(get("/test/temporary-auth/admin")
                        .header(TemporaryAuthHeaderVerifier.USER_ID_HEADER, "3")
                        .header(TemporaryAuthHeaderVerifier.ROLE_HEADER, "OPERATOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    /** 테스트 전용 controller이며 운영 코드에 포함되지 않는다. */
    @RestController
    @RequestMapping("/test/temporary-auth")
    static class TestController {

        private final TemporaryAuthHeaderVerifier verifier;

        TestController(TemporaryAuthHeaderVerifier verifier) {
            this.verifier = verifier;
        }

        @GetMapping("/operator")
        public ApiResponse<TestResponse> operator(HttpServletRequest request) {
            TemporaryAuthUserContext context = verifier.verifyOperatorOrHigher(request);
            return ApiResponse.of(new TestResponse(context.userId(), context.role().name()));
        }

        @GetMapping("/admin")
        public ApiResponse<TestResponse> admin(HttpServletRequest request) {
            TemporaryAuthUserContext context = verifier.verifyAdmin(request);
            return ApiResponse.of(new TestResponse(context.userId(), context.role().name()));
        }
    }

    /** 테스트 전용 응답 record. */
    record TestResponse(String userId, String role) {
    }
}
