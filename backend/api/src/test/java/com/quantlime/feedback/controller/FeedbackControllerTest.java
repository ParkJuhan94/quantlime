package com.quantlime.feedback.controller;

import com.quantlime.infra.slack.SlackWebhookClient;
import com.quantlime.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SlackWebhookClient는 실제 외부 HTTP 호출(RestClient.create())을 하므로
 * 목으로 대체한다 - 메시지 포맷팅은 FeedbackServiceTest에서 이미 검증한다.
 */
@Tag("integration")
class FeedbackControllerTest extends ApiTestSupport {

    @MockBean
    private SlackWebhookClient slackWebhookClient;

    @Test
    @DisplayName("[의견 보내기 성공 시 200을 반환하고 로그인 없이도 호출된다]")
    void sendFeedback_success_returns200WithoutAuth() throws Exception {
        // when & then
        mockMvc.perform(post("/api/feedback")
                .contentType("application/json")
                .content("""
                    {"category":"BUG","message":"차트가 안 보여요","pageUrl":"/stocks/005930"}
                    """))
            .andExpect(status().isOk());

        verify(slackWebhookClient, times(1)).sendMessage(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("[category가 BUG/FEATURE/OTHER가 아니면 400을 반환한다]")
    void sendFeedback_invalidCategory_returns400() throws Exception {
        mockMvc.perform(post("/api/feedback")
                .contentType("application/json")
                .content("""
                    {"category":"WRONG","message":"내용"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[message가 비어 있으면 400을 반환한다]")
    void sendFeedback_blankMessage_returns400() throws Exception {
        mockMvc.perform(post("/api/feedback")
                .contentType("application/json")
                .content("""
                    {"category":"BUG","message":""}
                    """))
            .andExpect(status().isBadRequest());
    }
}
