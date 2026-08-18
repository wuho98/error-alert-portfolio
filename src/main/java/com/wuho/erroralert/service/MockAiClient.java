package com.wuho.erroralert.service;

import org.springframework.stereotype.Component;

@Component
public class MockAiClient implements AiClient {
    @Override
    public String summarize(String prompt) {
        return "[Mock 요약] " + prompt.substring(0, Math.min(80, prompt.length()));
    }
}
