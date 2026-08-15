package com.quantlime.telegramfeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.stock.repository.StockRepository;
import com.quantlime.telegramfeed.domain.TelegramDigest;
import com.quantlime.telegramfeed.repository.TelegramDigestRepository;
import com.quantlime.telegramfeed.repository.TelegramDigestTickerRepository;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
import java.time.LocalDate;
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
class TelegramDigestPersistServiceTest {

    @Mock
    private TelegramDigestRepository telegramDigestRepository;

    @Mock
    private TelegramDigestTickerRepository telegramDigestTickerRepository;

    @Mock
    private StockRepository stockRepository;

    private TelegramDigestPersistService telegramDigestPersistService;

    private Channel channelOf() {
        return Channel.ofTelegram("insidertracking", "테스트 채널", 30,
            new TelegramFilterConfig(300, List.of(), List.of()));
    }

    private TelegramDigestPersistService newService() {
        return new TelegramDigestPersistService(
            telegramDigestRepository, telegramDigestTickerRepository, stockRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("[해당 채널×날짜에 다이제스트가 없으면 새로 생성하고, 실제 종목마스터에 있는 태깅 종목만 정규화한다]")
    void persistResult_noExistingDigest_createsNewAndSavesValidTickers() {
        // given
        telegramDigestPersistService = newService();
        Channel channel = channelOf();
        LocalDate date = LocalDate.of(2026, 8, 15);
        given(telegramDigestRepository.findByChannelAndDigestDate(channel, date)).willReturn(Optional.empty());
        given(telegramDigestRepository.save(any())).willAnswer(invocation -> {
            TelegramDigest digest = invocation.getArgument(0);
            ReflectionTestUtils.setField(digest, "id", 1L);
            return digest;
        });
        given(stockRepository.existsByStockCode("AAPL")).willReturn(true);
        SummarizeApiResponse response = new SummarizeApiResponse(
            "오늘의 요약", List.of("포인트1"), List.of("금리 인하 기대"),
            List.of(new SummarizeApiResponse.TickerMentionApiResponse("AAPL", "애플", "BULLISH", 0.8)),
            "고지", "gemini-3.5-flash-lite", 100, 50);

        // when
        telegramDigestPersistService.persistResult(channel, date, response);

        // then
        ArgumentCaptor<TelegramDigest> digestCaptor = ArgumentCaptor.forClass(TelegramDigest.class);
        verify(telegramDigestRepository).save(digestCaptor.capture());
        assertThat(digestCaptor.getValue().getDigestDate()).isEqualTo(date);
        assertThat(digestCaptor.getValue().getModel()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(digestCaptor.getValue().getPayload()).contains("\"summary\":\"오늘의 요약\"");
        verify(telegramDigestTickerRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("[해당 채널×날짜에 이미 다이제스트가 있으면 신규 생성 없이 덮어쓰고, 기존 태깅 종목을 전부 지운 뒤 새로 채운다]")
    void persistResult_existingDigest_overwritesAndReplacesTickers() {
        // given
        telegramDigestPersistService = newService();
        Channel channel = channelOf();
        LocalDate date = LocalDate.of(2026, 8, 15);
        TelegramDigest existing = TelegramDigest.of(channel, date, "gemini-3.5-flash-lite",
            "{\"summary\":\"이전 요약\",\"key_points\":[],\"mentioned_tickers\":[],\"caveat\":\"고지\"}", 50, 20);
        ReflectionTestUtils.setField(existing, "id", 1L);
        given(telegramDigestRepository.findByChannelAndDigestDate(channel, date)).willReturn(Optional.of(existing));
        given(stockRepository.existsByStockCode("AAPL")).willReturn(true);
        SummarizeApiResponse response = new SummarizeApiResponse(
            "누적된 오늘의 요약", List.of(), List.of(),
            List.of(new SummarizeApiResponse.TickerMentionApiResponse("AAPL", "애플", "BULLISH", 0.9)),
            "고지", "gemini-3.5-flash-lite", 200, 80);

        // when
        telegramDigestPersistService.persistResult(channel, date, response);

        // then
        verify(telegramDigestRepository, never()).save(any());
        assertThat(existing.getPayload()).contains("\"summary\":\"누적된 오늘의 요약\"");
        verify(telegramDigestTickerRepository).deleteByTelegramDigest(existing);
        verify(telegramDigestTickerRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("[실제 종목마스터에 없는(환각) 종목코드는 TelegramDigestTicker로 저장하지 않고 건너뛴다]")
    void persistResult_hallucinatedTickerCode_isSkipped() {
        // given
        telegramDigestPersistService = newService();
        Channel channel = channelOf();
        LocalDate date = LocalDate.of(2026, 8, 15);
        given(telegramDigestRepository.findByChannelAndDigestDate(channel, date)).willReturn(Optional.empty());
        given(telegramDigestRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(stockRepository.existsByStockCode("ZZZZ")).willReturn(false);
        SummarizeApiResponse response = new SummarizeApiResponse(
            "요약", List.of(), List.of(),
            List.of(new SummarizeApiResponse.TickerMentionApiResponse("ZZZZ", "없는종목", "NEUTRAL", 0.5)),
            "고지", "gemini-3.5-flash-lite", 100, 50);

        // when
        telegramDigestPersistService.persistResult(channel, date, response);

        // then
        verify(telegramDigestTickerRepository, never()).save(any());
    }
}
