package com.wuho.erroralert.api.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wuho.erroralert.api.common.ApiResponse;
import com.wuho.erroralert.api.common.GlobalExceptionHandler;
import jakarta.validation.Valid;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class ReceiveErrorEventRequestWebMvcTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new StubErrorController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void validRequestPassesValidation() throws Exception {
        mockMvc.perform(post("/test/errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "errorCode": "PAYMENT.PG_TIMEOUT",
                                  "message": "PG approval request timed out after 5000ms",
                                  "occurredAt": "2026-08-04T12:34:45Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorCode").value("PAYMENT.PG_TIMEOUT"))
                .andExpect(jsonPath("$.data.message").value("PG approval request timed out after 5000ms"));
    }

    @Test
    void missingRequiredFieldReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/test/errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "errorCode": "PAYMENT.PG_TIMEOUT",
                                  "occurredAt": "2026-08-04T12:34:45Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    void unsupportedErrorCodeReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/test/errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "errorCode": "PAYMENT.UNKNOWN",
                                  "message": "PG approval request timed out after 5000ms",
                                  "occurredAt": "2026-08-04T12:34:45Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    void malformedOccurredAtReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/test/errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "errorCode": "PAYMENT.PG_TIMEOUT",
                                  "message": "PG approval request timed out after 5000ms",
                                  "occurredAt": "not-a-time"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @RestController
    @RequestMapping("/test/errors")
    static class StubErrorController {

        @PostMapping
        ApiResponse<StubResponse> receive(@Valid @RequestBody ReceiveErrorEventRequest request) {
            return ApiResponse.of(new StubResponse(
                    request.errorCode(),
                    request.message(),
                    request.occurredAt()));
        }
    }

    record StubResponse(String errorCode, String message, Instant occurredAt) {
    }
}
