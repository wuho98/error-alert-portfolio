package com.wuho.erroralert.api;

import com.wuho.erroralert.api.common.ApiResponse;
import com.wuho.erroralert.api.common.auth.TemporaryAuthHeaderVerifier;
import com.wuho.erroralert.api.dto.ProjectApiKeyCreateResponse;
import com.wuho.erroralert.api.dto.ProjectCreateRequest;
import com.wuho.erroralert.api.dto.ProjectCreateResponse;
import com.wuho.erroralert.api.dto.ProjectSettingUpdateRequest;
import com.wuho.erroralert.api.dto.ProjectSettingUpdateResponse;
import com.wuho.erroralert.service.ProjectApiKeyService;
import com.wuho.erroralert.service.ProjectService;
import com.wuho.erroralert.service.ProjectSettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectApiKeyService projectApiKeyService;
    private final ProjectSettingService projectSettingService;
    private final TemporaryAuthHeaderVerifier authHeaderVerifier;

    public ProjectController(
            ProjectService projectService,
            ProjectApiKeyService projectApiKeyService,
            ProjectSettingService projectSettingService,
            TemporaryAuthHeaderVerifier authHeaderVerifier) {
        this.projectService = projectService;
        this.projectApiKeyService = projectApiKeyService;
        this.projectSettingService = projectSettingService;
        this.authHeaderVerifier = authHeaderVerifier;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectCreateResponse>> create(
            HttpServletRequest httpRequest,
            @Valid @RequestBody ProjectCreateRequest request) {
        authHeaderVerifier.verifyAdmin(httpRequest);
        ProjectCreateResponse response = projectService.create(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @PostMapping("/{projectId}/api-keys")
    public ResponseEntity<ApiResponse<ProjectApiKeyCreateResponse>> createApiKey(
            HttpServletRequest httpRequest,
            @PathVariable Long projectId) {
        authHeaderVerifier.verifyAdmin(httpRequest);
        ProjectApiKeyCreateResponse response = projectApiKeyService.create(projectId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @PutMapping("/{projectId}/settings")
    public ResponseEntity<ApiResponse<ProjectSettingUpdateResponse>> updateSetting(
            HttpServletRequest httpRequest,
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectSettingUpdateRequest request) {
        authHeaderVerifier.verifyAdmin(httpRequest);
        ProjectSettingUpdateResponse response = projectSettingService.update(projectId, request);
        return ResponseEntity.ok(ApiResponse.of(response));
    }
}
