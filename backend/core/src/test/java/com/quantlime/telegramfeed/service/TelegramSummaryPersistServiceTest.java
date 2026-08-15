package com.quantlime.telegramfeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.stock.repository.StockRepository;
import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostStatus;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.telegramfeed.repository.TelegramPostTickerRepository;
import com.quantlime.telegramfeed.repository.TelegramSummaryRepository;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TelegramSummaryPersistServiceTest {

    @Mock
    private TelegramPostRepository telegramPostRepository;

    @Mock
    private TelegramSummaryRepository telegramSummaryRepository;

    @Mock
    private TelegramPostTickerRepository telegramPostTickerRepository;

    @Mock
    private StockRepository stockRepository;

    private TelegramSummaryPersistService telegramSummaryPersistService;

    private TelegramPost postOf() {
        Channel channel = Channel.ofTelegram("insidertracking", "테스트 채널", 30,
            new TelegramFilterConfig(300, 2, List.of(), List.of()));
        TelegramPost post = TelegramPost.of(channel, "insidertracking/1", 1L, "본문 내용",
            LocalDateTime.now(), 100L, LocalDateTime.now(), false);
        ReflectionTestUtils.setField(post, "id", 1L);
        return post;
    }

    private TelegramSummaryPersistService newService() {
        return new TelegramSummaryPersistService(telegramPostRepository, telegramSummaryRepository,
            telegramPostTickerRepository, stockRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("[요약 결과를 저장하고, 실제 종목마스터에 있는 태깅 종목만 TelegramPostTicker로 정규화하며, 글을 SUMMARIZED로 전이한다]")
    void persistResult_savesSummaryAndValidTickers_marksSummarized() {
        // given
        telegramSummaryPersistService = newService();
        TelegramPost post = postOf();
        given(telegramPostRepository.findById(1L)).willReturn(Optional.of(post));
        given(stockRepository.existsByStockCode("AAPL")).willReturn(true);
        SummarizeApiResponse response = new SummarizeApiResponse(
            "요약", List.of("포인트1"), List.of("금리 인하 기대"),
            List.of(new SummarizeApiResponse.TickerMentionApiResponse("AAPL", "애플", "BULLISH", 0.8)),
            "고지", "gemini-3.5-flash-lite", 100, 50);

        // when
        telegramSummaryPersistService.persistResult(1L, response);

        // then
        ArgumentCaptor<com.quantlime.telegramfeed.domain.TelegramSummary> summaryCaptor =
            ArgumentCaptor.forClass(com.quantlime.telegramfeed.domain.TelegramSummary.class);
        verify(telegramSummaryRepository).save(summaryCaptor.capture());
        assertThat(summaryCaptor.getValue().getModel()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(summaryCaptor.getValue().getPayload()).contains("\"summary\":\"요약\"");
        assertThat(summaryCaptor.getValue().getPayload()).contains("AAPL");

        verify(telegramPostTickerRepository, times(1)).save(any());
        assertThat(post.getStatus()).isEqualTo(TelegramPostStatus.SUMMARIZED);
    }

    @Test
    @DisplayName("[실제 종목마스터에 없는(환각) 종목코드는 TelegramPostTicker로 저장하지 않고 건너뛴다]")
    void persistResult_hallucinatedTickerCode_isSkipped() {
        // given
        telegramSummaryPersistService = newService();
        TelegramPost post = postOf();
        given(telegramPostRepository.findById(1L)).willReturn(Optional.of(post));
        given(stockRepository.existsByStockCode("ZZZZ")).willReturn(false);
        SummarizeApiResponse response = new SummarizeApiResponse(
            "요약", List.of(), List.of(),
            List.of(new SummarizeApiResponse.TickerMentionApiResponse("ZZZZ", "없는종목", "NEUTRAL", 0.5)),
            "고지", "gemini-3.5-flash-lite", 100, 50);

        // when
        telegramSummaryPersistService.persistResult(1L, response);

        // then
        verify(telegramPostTickerRepository, never()).save(any());
        assertThat(post.getStatus()).isEqualTo(TelegramPostStatus.SUMMARIZED);
    }

    @Test
    @DisplayName("[markSummarizeFailed 호출 시 주어진 사유로 글을 FAILED 처리한다]")
    void markSummarizeFailed_marksPostFailedWithGivenReason() {
        // given
        telegramSummaryPersistService = newService();
        TelegramPost post = postOf();
        given(telegramPostRepository.findById(1L)).willReturn(Optional.of(post));

        // when
        telegramSummaryPersistService.markSummarizeFailed(1L, "Gemini 호출 실패");

        // then
        assertThat(post.getStatus()).isEqualTo(TelegramPostStatus.FAILED);
        assertThat(post.getFailReason()).isEqualTo("Gemini 호출 실패");
    }
}
