package com.wuho.erroralert.api.error;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import com.wuho.erroralert.api.common.ApiResponse;
import com.wuho.erroralert.api.common.auth.TemporaryAuthHeaderVerifier;
import com.wuho.erroralert.service.ErrorEventReceiveService;
import com.wuho.erroralert.service.ErrorEventQueryService;
import com.wuho.erroralert.service.ErrorTrendResult;
import com.wuho.erroralert.service.FindErrorTrendCommand;
import com.wuho.erroralert.service.FindRecentErrorEventsCommand;
import com.wuho.erroralert.service.RecentErrorEventsResult;
import com.wuho.erroralert.service.ReceiveErrorEventCommand;
import com.wuho.erroralert.service.ReceiveErrorEventResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/errors")
public class ErrorEventController {

    private static final String API_KEY_HEADER = "X-Api-Key";

    private final ErrorEventReceiveService errorEventReceiveService;
    private final ErrorEventQueryService errorEventQueryService;
    private final TemporaryAuthHeaderVerifier authHeaderVerifier;

    public ErrorEventController(
            ErrorEventReceiveService errorEventReceiveService,
            ErrorEventQueryService errorEventQueryService,
            TemporaryAuthHeaderVerifier authHeaderVerifier
    ) {
        this.errorEventReceiveService = errorEventReceiveService;
        this.errorEventQueryService = errorEventQueryService;
        this.authHeaderVerifier = authHeaderVerifier;
    }

    @GetMapping
    public ApiResponse<RecentErrorEventsResponse> findRecentErrors(
            HttpServletRequest httpRequest,
            @Valid @ModelAttribute RecentErrorEventsRequest request,
            BindingResult bindingResult
    ) {
        authHeaderVerifier.verifyOperatorOrHigher(httpRequest);
        if (bindingResult.hasErrors()) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }

        FindRecentErrorEventsCommand command = request.toCommand();
        RecentErrorEventsResult result = errorEventQueryService.findRecent(command);
        return ApiResponse.of(RecentErrorEventsResponse.from(result));
    }

    @GetMapping("/trend")
    public ApiResponse<ErrorTrendResponse> findErrorTrend(
            HttpServletRequest httpRequest,
            @Valid @ModelAttribute ErrorTrendRequest request,
            BindingResult bindingResult
    ) {
        authHeaderVerifier.verifyOperatorOrHigher(httpRequest);
        if (bindingResult.hasErrors()) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }

        FindErrorTrendCommand command = request.toCommand();
        ErrorTrendResult result = errorEventQueryService.findTrend(command);
        return ApiResponse.of(ErrorTrendResponse.from(result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReceiveErrorEventResponse>> receive(
            @RequestHeader(name = API_KEY_HEADER, required = false) String rawApiKey,
            @Valid @RequestBody ReceiveErrorEventRequest request
    ) {
        ReceiveErrorEventCommand command = new ReceiveErrorEventCommand(
                request.errorCode(),
                request.message(),
                request.occurredAt()
        );
        ReceiveErrorEventResult result = errorEventReceiveService.receive(rawApiKey, command);
        ReceiveErrorEventResponse response = ReceiveErrorEventResponse.from(result);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }
}
