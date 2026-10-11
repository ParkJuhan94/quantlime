package com.quantlime.telegramfeed.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quantlime.common.exception.GlobalExceptionHandler;
import com.quantlime.telegramfeed.dto.TelegramCollectResult;
import com.quantlime.telegramfeed.dto.TelegramRetentionResult;
import com.quantlime.telegramfeed.service.TelegramChannelQueryService;
import com.quantlime.telegramfeed.service.TelegramCollectionFacade;
import com.quantlime.telegramfeed.service.TelegramDigestGenerationFacade;
import com.quantlime.telegramfeed.service.TelegramPostRetentionService;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** {@link com.quantlime.videofeed.controller.FeedCollectionAdminControllerTest}와 같은 이유로 standalone 방식이다. */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TelegramFeedAdminControllerTest {

    @Mock
    private TelegramCollectionFacade telegramCollectionFacade;

    @Mock
    private TelegramDigestGenerationFacade telegramDigestGenerationFacade;

    @Mock
    private TelegramPostRetentionService telegramPostRetentionService;

    @Mock
    private TelegramChannelQueryService telegramChannelQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TelegramFeedAdminController controller = new TelegramFeedAdminController(
            telegramCollectionFacade, telegramDigestGenerationFacade, telegramPostRetentionService,
            telegramChannelQueryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("[채널 목록은 t.me 링크를 포함한 응답으로 변환해 돌려준다]")
    void channels_mapsToResponse() throws Exception {
        Channel channel = Channel.ofTelegram("insider", "인사이더", 30, new TelegramFilterConfig(200, List.of()));
        given(telegramChannelQueryService.findAllOrderByPriority()).willReturn(List.of(channel));

        mockMvc.perform(get("/api/admin/telegram-feed/channels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("인사이더"))
            .andExpect(jsonPath("$[0].channelUrl").value("https://t.me/insider"));
    }

    @Test
    @DisplayName("[수집 트리거 - 락을 잡으면 결과를, 이미 실행 중이면 400을 돌려준다]")
    void collect_returnsResultsOrRejects() throws Exception {
        given(telegramCollectionFacade.runAllExclusively())
            .willReturn(Optional.of(List.of(TelegramCollectResult.success("인사이더", 5))))
            .willReturn(Optional.empty());

        mockMvc.perform(post("/api/admin/telegram-feed/collect"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].discoveredCount").value(5));
        mockMvc.perform(post("/api/admin/telegram-feed/collect")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[다이제스트 이벤트 재발행은 발행 건수를 돌려주고, 이미 실행 중이면 400이다]")
    void generateDigest_returnsPublishedCountOrRejects() throws Exception {
        given(telegramDigestGenerationFacade.runAllExclusively()).willReturn(Optional.of(3)).willReturn(Optional.empty());

        mockMvc.perform(post("/api/admin/telegram-feed/digest/generate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(3));
        mockMvc.perform(post("/api/admin/telegram-feed/digest/generate")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[보존 기간 정리 - 삭제한 글·다이제스트 건수를 돌려주고, 이미 실행 중이면 400 TF_004다]")
    void cleanupRetention_returnsResultOrRejects() throws Exception {
        given(telegramPostRetentionService.runExclusively())
            .willReturn(Optional.of(new TelegramRetentionResult(8, 2)))
            .willReturn(Optional.empty());

        mockMvc.perform(post("/api/admin/telegram-feed/retention/cleanup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedPostCount").value(8))
            .andExpect(jsonPath("$.deletedDigestCount").value(2));
        mockMvc.perform(post("/api/admin/telegram-feed/retention/cleanup"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TF_004"));
    }
}
