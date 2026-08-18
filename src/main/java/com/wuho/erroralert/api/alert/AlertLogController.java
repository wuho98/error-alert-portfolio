package com.wuho.erroralert.api.alert;

import com.wuho.erroralert.api.common.ApiResponse;
import com.wuho.erroralert.api.common.auth.TemporaryAuthHeaderVerifier;
import com.wuho.erroralert.service.AlertLogQueryService;
import com.wuho.erroralert.service.AlertLogsResult;
import com.wuho.erroralert.service.FindAlertLogsCommand;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/alerts")
public class AlertLogController {

    private final TemporaryAuthHeaderVerifier authHeaderVerifier;
    private final AlertLogQueryService alertLogQueryService;

    @GetMapping
    public ApiResponse<AlertLogsResponse> findAlerts(
            HttpServletRequest httpRequest,
            @ModelAttribute AlertLogsRequest request
    ) {
        authHeaderVerifier.verifyOperatorOrHigher(httpRequest);

        FindAlertLogsCommand command = request.toCommand();
        AlertLogsResult result = alertLogQueryService.findAlerts(command);
        return ApiResponse.of(AlertLogsResponse.from(result));
    }
}
