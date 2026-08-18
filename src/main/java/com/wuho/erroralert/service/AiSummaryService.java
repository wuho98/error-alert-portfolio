package com.wuho.erroralert.service;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSummaryService {

    private final AiClient aiClient;
    private final SensitiveValueMasker masker;

    public String summarize(AlertSummaryInput input) {
        String prompt = """
                다음 결제 오류 알림을 운영자가 공유할 수 있게 3문장 이내로 요약해줘.
                프로젝트: %d / 오류 코드: %s / 발생 구간: %s
                관측 횟수: %d건 (임계값 %d건) / 알림 상태: %s
                """.formatted(input.projectId(), input.errorCode(), input.windowStartedAt(),
            input.observedCount(), input.threshold(), input.status());

        String maskedPrompt = masker.mask(prompt);

        try {
            return aiClient.summarize(maskedPrompt);
        } catch (Exception e) {
            log.error("AI 요약 호출 실패", e);
            throw new ApiException(ApiErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }
}
