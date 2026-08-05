package com.lifewise.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lifewise.ai.dto.ConsentRequest;
import com.lifewise.ai.dto.ConsentView;
import com.lifewise.ai.service.ConsentVerifier;
import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.userprofile.UserProfileConsentReader;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * AiConsentController 单元测试（plan-06-ai §2.5）。
 *
 * <p>覆盖 GET / POST 两条路径。
 */
@ExtendWith(MockitoExtension.class)
class AiConsentControllerTest {

    private static final Long USER_ID = 1L;

    @Mock ConsentVerifier consentVerifier;
    @Mock UserProfileConsentReader consentReader;
    AiConsentController controller;

    @BeforeEach
    void setUp() {
        controller = new AiConsentController(consentVerifier, consentReader);
    }

    @Test
    @DisplayName("GET /api/ai/consent returns current consent state")
    void get_returnsCurrentState() {
        OffsetDateTime now = OffsetDateTime.now();
        when(consentReader.isAiConsentGranted(USER_ID)).thenReturn(true);
        when(consentReader.getAiConsentAt(USER_ID)).thenReturn(now);

        ApiResponse<ConsentView> resp = controller.get(USER_ID);

        assertThat(resp.success()).isTrue();
        assertThat(resp.data().aiConsent()).isTrue();
        assertThat(resp.data().consentedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("POST /api/ai/consent grants or revokes through ConsentVerifier")
    void update_passesThroughToVerifier() {
        OffsetDateTime when = OffsetDateTime.now();
        when(consentVerifier.updateConsent(USER_ID, true))
                .thenReturn(Optional.of(new ConsentView(true, when)));

        ResponseEntity<ApiResponse<ConsentView>> resp = controller.update(
                USER_ID, new ConsentRequest(true));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().data().aiConsent()).isTrue();
        assertThat(resp.getBody().data().consentedAt()).isEqualTo(when);
    }

    @Test
    @DisplayName("POST /api/ai/consent rejects null aiConsent with 400")
    void update_nullConsent_returns400() {
        ResponseEntity<ApiResponse<ConsentView>> resp = controller.update(
                USER_ID, new ConsentRequest(null));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().success()).isFalse();
        assertThat(resp.getBody().error().code()).isEqualTo("INVALID_INPUT");
    }
}