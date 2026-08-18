package com.wuho.erroralert.api.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerWebMvcTest.TestController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownPathReturnsCommonNotFoundInWebMvcContext() throws Exception {
        mockMvc.perform(get("/test/webmvc/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("C003"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/webmvc/ping")
        ApiResponse<TestResponse> ping() {
            return ApiResponse.of(new TestResponse("pong"));
        }
    }

    record TestResponse(String value) {
    }
}
