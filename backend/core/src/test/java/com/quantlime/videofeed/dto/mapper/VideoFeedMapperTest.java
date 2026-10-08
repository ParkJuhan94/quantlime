package com.quantlime.videofeed.dto.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoTicker;
import com.quantlime.videofeed.dto.SummaryPayload;
import com.quantlime.videofeed.dto.response.ChannelResponse;
import com.quantlime.videofeed.dto.response.VideoFeedDetailResponse;
import com.quantlime.videofeed.dto.response.VideoFeedItemResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
class VideoFeedMapperTest {

    private static final LocalDateTime PUBLISHED = LocalDateTime.of(2026, 9, 1, 9, 0);

    private Channel youtubeChannel() {
        Channel channel = Channel.of(Platform.YOUTUBE, "UCabc", "UUabc", "유튜브 채널", 10,
            new ChannelFilterConfig(180, 0.0, 5, List.of(), List.of()));
        ReflectionTestUtils.setField(channel, "id", 3L);
        return channel;
    }

    private Video videoOf(Channel channel) {
        Video video = Video.of(channel, "vid123", "제목", PUBLISHED, 600, 1000L, PUBLISHED);
        ReflectionTestUtils.setField(video, "id", 7L);
        return video;
    }

    @Test
    @DisplayName("[목록 항목은 채널·영상 URL을 만들고 종목 태그를 응답으로 옮긴다]")
    void toItemResponse_buildsUrlsAndTickers() {
        Video video = videoOf(youtubeChannel());
        VideoTicker ticker = VideoTicker.of(video, "005930", "삼성전자", "BULLISH", new BigDecimal("0.90"));

        VideoFeedItemResponse response = VideoFeedMapper.toItemResponse(video, "요약", List.of(ticker));

        assertThat(response.videoId()).isEqualTo(7L);
        assertThat(response.channelUrl()).isEqualTo("https://www.youtube.com/channel/UCabc");
        assertThat(response.videoUrl()).isEqualTo("https://www.youtube.com/watch?v=vid123");
        assertThat(response.summary()).isEqualTo("요약");
        assertThat(response.durationSec()).isEqualTo(600);
        assertThat(response.tickers()).singleElement().satisfies(t -> {
            assertThat(t.tickerCode()).isEqualTo("005930");
            assertThat(t.stance()).isEqualTo("BULLISH");
            assertThat(t.confidence()).isEqualByComparingTo("0.90");
        });
    }

    @Test
    @DisplayName("[상세 응답은 요약 payload를 풀어 담는다]")
    void toDetailResponse_unpacksPayload() {
        Video video = videoOf(youtubeChannel());
        SummaryPayload payload = new SummaryPayload("요약", List.of("포인트1"), List.of("매크로1"), "주의");

        VideoFeedDetailResponse response = VideoFeedMapper.toDetailResponse(video, payload, List.of());

        assertThat(response.keyPoints()).containsExactly("포인트1");
        assertThat(response.macroPoints()).containsExactly("매크로1");
        assertThat(response.caveat()).isEqualTo("주의");
        assertThat(response.tickers()).isEmpty();
    }

    @Test
    @DisplayName("[macro_points가 없던 과거 요약은 null이 아니라 빈 리스트로 내려준다]")
    void toDetailResponse_legacyPayloadWithoutMacroPoints_returnsEmptyList() {
        Video video = videoOf(youtubeChannel());
        SummaryPayload legacy = new SummaryPayload("요약", List.of("포인트1"), null, null);

        VideoFeedDetailResponse response = VideoFeedMapper.toDetailResponse(video, legacy, List.of());

        assertThat(response.macroPoints()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("[채널 응답은 플랫폼별 URL을 만들고, 텔레그램은 t.me 링크를 쓴다]")
    void toChannelResponse_platformSpecificUrl() {
        Channel telegram = Channel.ofTelegram("insider", "텔레그램 채널", 5,
            new TelegramFilterConfig(0, List.of()));

        ChannelResponse youtube = VideoFeedMapper.toChannelResponse(youtubeChannel());
        ChannelResponse tg = VideoFeedMapper.toChannelResponse(telegram);

        assertThat(youtube.channelUrl()).isEqualTo("https://www.youtube.com/channel/UCabc");
        assertThat(youtube.platform()).isEqualTo(Platform.YOUTUBE);
        assertThat(youtube.enabled()).isTrue();
        assertThat(tg.channelUrl()).isEqualTo("https://t.me/insider");
        assertThat(tg.platform()).isEqualTo(Platform.TELEGRAM);
    }

    @Test
    @DisplayName("[피드 필터용 채널 응답은 id와 이름만 담는다]")
    void toVideoFeedChannelResponse_idAndName() {
        assertThat(VideoFeedMapper.toVideoFeedChannelResponse(youtubeChannel()))
            .satisfies(r -> {
                assertThat(r.channelId()).isEqualTo(3L);
                assertThat(r.name()).isEqualTo("유튜브 채널");
            });
    }
}
