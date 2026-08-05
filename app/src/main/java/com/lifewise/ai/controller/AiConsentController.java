package com.lifewise.ai.controller;

import com.lifewise.ai.dto.ConsentRequest;
import com.lifewise.ai.dto.ConsentView;
import com.lifewise.ai.service.ConsentVerifier;
import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.shared.integration.dto.ErrorEnvelope;
import com.lifewise.task.web.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 用户同意端点（plan-06-ai §2.5；BR-26）。
 *
 * <p>2 端点：
 * <ul>
 *   <li>GET  /api/ai/consent  — 读取当前同意状态</li>
 *   <li>POST /api/ai/consent  — 授予 / 撤销（写 user_profiles.ai_consent + audit）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai/consent")
public class AiConsentController {

    private final ConsentVerifier consentVerifier;
    private final com.lifewise.userprofile.UserProfileConsentReader consentReader;

    public AiConsentController(ConsentVerifier consentVerifier,
                               com.lifewise.userprofile.UserProfileConsentReader consentReader) {
        this.consentVerifier = consentVerifier;
        this.consentReader = consentReader;
    }

    @GetMapping
    public ApiResponse<ConsentView> get(@CurrentUser Long userId) {
        boolean granted = consentReader.isAiConsentGranted(userId);
        return ApiResponse.ok(new ConsentView(granted, consentReader.getAiConsentAt(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConsentView>> update(
            @CurrentUser Long userId,
            @Valid @RequestBody ConsentRequest req) {
        if (req.aiConsent() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(new ErrorEnvelope("INVALID_INPUT", "aiConsent required", null, null)));
        }
        ConsentView view = consentVerifier.updateConsent(userId, req.aiConsent())
                .orElseThrow(() -> new IllegalStateException("consent update returned empty"));
        return ResponseEntity.ok(ApiResponse.ok(view));
    }
}