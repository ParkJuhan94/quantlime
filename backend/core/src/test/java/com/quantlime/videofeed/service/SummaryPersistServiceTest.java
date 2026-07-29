package com.quantlime.videofeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.stock.repository.StockRepository;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import com.quantlime.videofeed.repository.SummaryRepository;
import com.quantlime.videofeed.repository.VideoRepository;
import com.quantlime.videofeed.repository.VideoTickerRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class SummaryPersistServiceTest {

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private SummaryRepository summaryRepository;

    @Mock
    private VideoTickerRepository videoTickerRepository;

    @Mock
    private StockRepository stockRepository;

    private SummaryPersistService summaryPersistService;

    private Video videoOf() {
        Channel channel = Channel.of(Platform.YOUTUBE, "UCtest", "UUtest", "테스트 채널", 10,
            new ChannelFilterConfig(180, 1.5, 5, List.of(), List.of()));
        Video video = Video.of(channel, "vid-1", "제목", LocalDateTime.now(), 300, 100L, LocalDateTime.now());
        ReflectionTestUtils.setField(video, "id", 1L);
        return video;
    }

    private SummaryPersistService newService() {
        return new SummaryPersistService(
            videoRepository, summaryRepository, videoTickerRepository, stockRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("[요약 결과를 저장하고, 실제 종목마스터에 있는 태깅 종목만 VideoTicker로 정규화하며, 영상을 SUMMARIZED로 전이한다]")
    void persistResult_savesSummaryAndValidTickers_marksSummarized() {
        // given
        summaryPersistService = newService();
        Video video = videoOf();
        given(videoRepository.findById(1L)).willReturn(Optional.of(video));
        given(stockRepository.existsByStockCode("005930")).willReturn(true);
        SummarizeApiResponse response = new SummarizeApiResponse(
            "요약", List.of("포인트1"),
            List.of(new SummarizeApiResponse.TickerMentionApiResponse("005930", "삼성전자", "BULLISH", 0.8)),
            "고지", "gemini-2.5-flash", 100, 50);

        // when
        summaryPersistService.persistResult(1L, response);

        // then
        ArgumentCaptor<com.quantlime.videofeed.domain.Summary> summaryCaptor =
            ArgumentCaptor.forClass(com.quantlime.videofeed.domain.Summary.class);
        verify(summaryRepository).save(summaryCaptor.capture());
        assertThat(summaryCaptor.getValue().getModel()).isEqualTo("gemini-2.5-flash");
        assertThat(summaryCaptor.getValue().getPayload()).contains("\"summary\":\"요약\"");
        assertThat(summaryCaptor.getValue().getPayload()).contains("005930");

        verify(videoTickerRepository, times(1)).save(org.mockito.ArgumentMatchers.any());
        assertThat(video.getStatus()).isEqualTo(VideoStatus.SUMMARIZED);
    }

    @Test
    @DisplayName("[실제 종목마스터에 없는(환각) 종목코드는 VideoTicker로 저장하지 않고 건너뛴다]")
    void persistResult_hallucinatedTickerCode_isSkipped() {
        // given
        summaryPersistService = newService();
        Video video = videoOf();
        given(videoRepository.findById(1L)).willReturn(Optional.of(video));
        given(stockRepository.existsByStockCode("999999")).willReturn(false);
        SummarizeApiResponse response = new SummarizeApiResponse(
            "요약", List.of(),
            List.of(new SummarizeApiResponse.TickerMentionApiResponse("999999", "없는종목", "NEUTRAL", 0.5)),
            "고지", "gemini-2.5-flash", 100, 50);

        // when
        summaryPersistService.persistResult(1L, response);

        // then
        verify(videoTickerRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(video.getStatus()).isEqualTo(VideoStatus.SUMMARIZED);
    }

    @Test
    @DisplayName("[markSummarizeFailed 호출 시 주어진 사유로 영상을 FAILED 처리한다]")
    void markSummarizeFailed_marksVideoFailedWithGivenReason() {
        // given
        summaryPersistService = newService();
        Video video = videoOf();
        given(videoRepository.findById(1L)).willReturn(Optional.of(video));

        // when
        summaryPersistService.markSummarizeFailed(1L, "Gemini 호출 실패");

        // then
        assertThat(video.getStatus()).isEqualTo(VideoStatus.FAILED);
        assertThat(video.getFailReason()).isEqualTo("Gemini 호출 실패");
    }
}
