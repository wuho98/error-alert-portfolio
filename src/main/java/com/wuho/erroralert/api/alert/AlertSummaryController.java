package com.wuho.erroralert.api.alert;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import com.wuho.erroralert.api.common.ApiResponse;
import com.wuho.erroralert.api.common.auth.TemporaryAuthHeaderVerifier;
import com.wuho.erroralert.service.AiSummaryService;
import com.wuho.erroralert.service.AlertSummaryInput;
import com.wuho.erroralert.service.AlertSummaryProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/alerts")
public class AlertSummaryController {

    private final TemporaryAuthHeaderVerifier authHeaderVerifier;
    private final AlertSummaryProvider alertSummaryProvider;
    private final AiSummaryService aiSummaryService;

    @PostMapping("/{alertId}/summary")
    public ApiResponse<AlertSummaryResponse> summarize(@PathVariable Long alertId,
                                                       HttpServletRequest request) {
        authHeaderVerifier.verifyOperatorOrHigher(request);

        AlertSummaryInput input = alertSummaryProvider.findAlert(alertId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.ALERT_NOT_FOUND));

        String situation = aiSummaryService.summarize(input);
        return ApiResponse.of(AlertSummaryResponse.of(alertId, situation));
    }
}
