package com.quantlime.videofeed.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.quantlime.common.exception.GlobalExceptionHandler;
import com.quantlime.infra.sync.dto.TranscriptImportRequest;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.dto.CollectResult;
import com.quantlime.videofeed.dto.TranscriptImportResult;
import com.quantlime.videofeed.service.ChannelQueryService;
import com.quantlime.videofeed.service.ChannelVelocityInitializationService;
import com.quantlime.videofeed.service.FeedCollectionFacade;
import com.quantlime.videofeed.service.SummaryCollectionFacade;
import com.quantlime.videofeed.service.TranscriptCollectionFacade;
import com.quantlime.videofeed.service.TranscriptImportService;
import com.quantlime.videofeed.service.VideoRetentionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 관리자 수동 트리거 컨트롤러의 라우팅·응답 변환·"이미 실행 중" 거절을 검증한다. 전체 컨텍스트를 띄우지 않는
 * standalone 방식이라 컨텍스트 캐시(Kafka 리스너 폭주)에 영향을 주지 않는다 - 권한(ROLE_ADMIN)은
 * SecurityConfig 몫이라 여기서 다루지 않는다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class FeedCollectionAdminControllerTest {

    @Mock
    private FeedCollectionFacade feedCollectionFacade;

    @Mock
    private ChannelVelocityInitializationService channelVelocityInitializationService;

    @Mock
    private TranscriptCollectionFacade transcriptCollectionFacade;

    @Mock
    private SummaryCollectionFacade summaryCollectionFacade;

    @Mock
    private VideoRetentionService videoRetentionService;

    @Mock
    private ChannelQueryService channelQueryService;

    @Mock
    private TranscriptImportService transcriptImportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FeedCollectionAdminController controller = new FeedCollectionAdminController(
            feedCollectionFacade, channelVelocityInitializationService, transcriptCollectionFacade,
            summaryCollectionFacade, videoRetentionService, channelQueryService, transcriptImportService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("[채널 목록은 우선순위 순 채널을 응답 DTO로 변환해 돌려준다]")
    void channels_mapsToResponse() throws Exception {
        Channel channel = Channel.of(Platform.YOUTUBE, "UCabc", "UUabc", "테스트 채널", 10,
            new ChannelFilterConfig(180, 0.0, 5, List.of(), List.of()));
        given(channelQueryService.findAllOrderByPriority()).willReturn(List.of(channel));

        mockMvc.perform(get("/api/admin/feed/channels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("테스트 채널"))
            .andExpect(jsonPath("$[0].channelUrl").value("https://www.youtube.com/channel/UCabc"));
    }

    @Test
    @DisplayName("[수집 트리거 - 락을 잡으면 채널별 결과를, 이미 실행 중이면 400 VF_005를 돌려준다]")
    void collect_returnsResultsOrRejectsWhenAlreadyRunning() throws Exception {
        given(feedCollectionFacade.runAllExclusively())
            .willReturn(Optional.of(List.of(CollectResult.success("채널", 3))))
            .willReturn(Optional.empty());

        mockMvc.perform(post("/api/admin/feed/collect"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].channelName").value("채널"))
            .andExpect(jsonPath("$[0].discoveredCount").value(3));
        mockMvc.perform(post("/api/admin/feed/collect"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VF_005"));
    }

    @Test
    @DisplayName("[자막·요약 이벤트 재발행은 발행 건수를 그대로 돌려준다]")
    void transcribeAndSummarize_returnPublishedCount() throws Exception {
        given(transcriptCollectionFacade.publishBacklog()).willReturn(7);
        given(summaryCollectionFacade.publishBacklog()).willReturn(4);

        mockMvc.perform(post("/api/admin/feed/transcribe")).andExpect(status().isOk()).andExpect(jsonPath("$").value(7));
        mockMvc.perform(post("/api/admin/feed/summarize")).andExpect(status().isOk()).andExpect(jsonPath("$").value(4));
    }

    @Test
    @DisplayName("[채널 velocity 초기 산정은 경로의 channelId로 위임한다]")
    void initializeVelocity_delegatesWithPathVariable() throws Exception {
        given(channelVelocityInitializationService.initializeMedianVelocity(5L)).willReturn(new BigDecimal("12.5"));

        mockMvc.perform(post("/api/admin/feed/channels/5/velocity/initialize"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(12.5));
    }

    @Test
    @DisplayName("[자막 임포트는 항목별 결과를 돌려주고, 빈 목록은 400으로 거절한다]")
    void importTranscripts_validatesAndReturnsResults() throws Exception {
        given(transcriptImportService.importAll(org.mockito.ArgumentMatchers.any(TranscriptImportRequest.class)))
            .willReturn(List.of(new TranscriptImportResult("vid1", TranscriptImportResult.Outcome.IMPORTED, null)));
        String valid = "{\"items\":[{\"externalVideoId\":\"vid1\",\"source\":\"local\",\"lang\":\"ko\","
            + "\"content\":\"본문\",\"charCount\":2}]}";

        mockMvc.perform(post("/api/admin/feed/transcripts/import").contentType(MediaType.APPLICATION_JSON).content(valid))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].outcome").value("IMPORTED"));
        mockMvc.perform(post("/api/admin/feed/transcripts/import").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("[보존 기간 정리 - 락을 잡으면 삭제 건수를, 이미 실행 중이면 400 VF_006을 돌려준다]")
    void cleanupRetention_returnsCountOrRejects() throws Exception {
        given(videoRetentionService.runExclusively()).willReturn(Optional.of(11)).willReturn(Optional.empty());

        mockMvc.perform(post("/api/admin/feed/retention/cleanup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(11));
        mockMvc.perform(post("/api/admin/feed/retention/cleanup"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VF_006"));
    }
}
