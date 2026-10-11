package com.quantlime.telegramfeed.dto.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.quantlime.telegramfeed.domain.TelegramDigest;
import com.quantlime.telegramfeed.domain.TelegramDigestTicker;
import com.quantlime.telegramfeed.dto.TelegramSummaryPayload;
import com.quantlime.telegramfeed.dto.response.TelegramChannelResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedDigestDetailResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedDigestResponse;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
class TelegramFeedMapperTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 1);

    private Channel channel() {
        Channel channel = Channel.ofTelegram("insider", "인사이더", 30, new TelegramFilterConfig(200, List.of("광고")));
        ReflectionTestUtils.setField(channel, "id", 4L);
        return channel;
    }

    private TelegramDigest digest(Channel channel) {
        TelegramDigest digest = TelegramDigest.of(channel, DATE, "gemini", "{}", 10, 20);
        ReflectionTestUtils.setField(digest, "id", 9L);
        return digest;
    }

    @Test
    @DisplayName("[채널 응답은 t.me 링크와 필터 설정을 담는다]")
    void toChannelResponse_buildsTmeUrl() {
        TelegramChannelResponse response = TelegramFeedMapper.toChannelResponse(channel());

        assertThat(response.channelUrl()).isEqualTo("https://t.me/insider");
        assertThat(response.name()).isEqualTo("인사이더");
        assertThat(response.priority()).isEqualTo(30);
        assertThat(response.enabled()).isTrue();
    }

    @Test
    @DisplayName("[다이제스트 목록 응답은 원문 건수와 종목 태그를 함께 담는다]")
    void toDigestResponse_includesSourceCountAndTickers() {
        TelegramDigest digest = digest(channel());
        TelegramDigestTicker ticker = TelegramDigestTicker.of(digest, "AAPL", "애플", "BULLISH", new BigDecimal("0.80"));

        TelegramFeedDigestResponse response = TelegramFeedMapper.toDigestResponse(digest, "요약", List.of(ticker), 12);

        assertThat(response.telegramDigestId()).isEqualTo(9L);
        assertThat(response.channelName()).isEqualTo("인사이더");
        assertThat(response.channelUrl()).isEqualTo("https://t.me/insider");
        assertThat(response.digestDate()).isEqualTo(DATE);
        assertThat(response.sourcePostCount()).isEqualTo(12);
        assertThat(response.tickers()).singleElement().satisfies(t -> {
            assertThat(t.tickerCode()).isEqualTo("AAPL");
            assertThat(t.stance()).isEqualTo("BULLISH");
        });
    }

    @Test
    @DisplayName("[상세 응답은 원문 링크와 요약 payload를 담고, macro_points가 없으면 빈 리스트다]")
    void toDigestDetailResponse_unpacksPayloadAndGuardsNullMacroPoints() {
        TelegramDigest digest = digest(channel());
        TelegramSummaryPayload withMacro = new TelegramSummaryPayload("요약", List.of("포인트"), List.of("매크로"), "주의");
        TelegramSummaryPayload legacy = new TelegramSummaryPayload("요약", List.of("포인트"), null, null);

        TelegramFeedDigestDetailResponse full = TelegramFeedMapper.toDigestDetailResponse(
            digest, withMacro, List.of(), List.of("https://t.me/insider/1"));
        TelegramFeedDigestDetailResponse old = TelegramFeedMapper.toDigestDetailResponse(
            digest, legacy, List.of(), List.of());

        assertThat(full.sourcePostUrls()).containsExactly("https://t.me/insider/1");
        assertThat(full.macroPoints()).containsExactly("매크로");
        assertThat(full.caveat()).isEqualTo("주의");
        assertThat(old.macroPoints()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("[피드 필터용 채널 응답은 id와 이름만 담는다]")
    void toFeedChannelResponse_idAndName() {
        assertThat(TelegramFeedMapper.toFeedChannelResponse(channel())).satisfies(r -> {
            assertThat(r.channelId()).isEqualTo(4L);
            assertThat(r.name()).isEqualTo("인사이더");
        });
    }
}
