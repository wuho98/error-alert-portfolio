package com.wuho.erroralert.service;

import org.springframework.stereotype.Component;

@Component
public class SensitiveValueMasker {

    private static final String MASK = "[MASKED]";

    public String mask(String input) {
        if (input == null) {
            return null;
        }
        return input
            .replaceAll("https?://\\S+", MASK)
            .replaceAll("(?i)(pk|sk|key|token)_[A-Za-z0-9_-]+", MASK)
            .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._-]+", MASK);
    }
}
