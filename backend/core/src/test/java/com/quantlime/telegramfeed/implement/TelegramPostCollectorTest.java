package com.quantlime.telegramfeed.implement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.infra.telegram.TelegramApiProperties;
import com.quantlime.infra.telegram.TelegramWebPreviewClient;
import com.quantlime.infra.telegram.dto.TelegramPreviewMessage;
import com.quantlime.infra.telegram.dto.TelegramPreviewPage;
import com.quantlime.telegramfeed.dto.TelegramCollectionOutcome;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 외부 스크래핑(I/O)만 담당하는 수집기라 페이지네이션 방향(증분은 afterId, 최초는 beforeId)과 종료 조건
 * (빈 페이지, 최대 페이지 수, 보존기간 컷)이 핵심이다. 페이지 간 딜레이는 0ms로 둬 테스트를 빠르게 한다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TelegramPostCollectorTest {

    private static final String HANDLE = "stock_channel";

    @Mock
    private TelegramWebPreviewClient previewClient;

    @Mock
    private TelegramPostReader telegramPostReader;

    private TelegramPostCollector collector;
    private Channel channel;

    @BeforeEach
    void setUp() {
        collector = new TelegramPostCollector(previewClient, telegramPostReader,
            new TelegramApiProperties("https://t.me/s", "test-agent", 0L));
        channel = Channel.of(Platform.TELEGRAM, HANDLE, HANDLE, "텔레그램 채널", 10,
            new ChannelFilterConfig(0, 0.0, 0, List.of(), List.of()));
    }

    private TelegramPreviewMessage message(long id, LocalDateTime publishedAt) {
        return new TelegramPreviewMessage(HANDLE + "/" + id, id, "본문" + id, publishedAt, 100L, false);
    }

    private TelegramPreviewPage page(String title, TelegramPreviewMessage... messages) {
        return new TelegramPreviewPage(title, "https://photo/" + title, List.of(messages));
    }

    @Test
    @DisplayName("[저장된 글이 있으면 증분 수집: 마지막 messageId 이후 페이지를 이어 받고 메타는 첫 페이지 것을 쓴다]")
    void collect_incremental_followsCursorForward() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(telegramPostReader.findLastMessageId(channel)).willReturn(Optional.of(100L));
        given(previewClient.fetchPage(HANDLE, 100L, null))
            .willReturn(page("첫페이지제목", message(101, now), message(103, now), message(102, now)));
        given(previewClient.fetchPage(HANDLE, 103L, null)).willReturn(page("무시될제목", message(104, now)));
        given(previewClient.fetchPage(HANDLE, 104L, null)).willReturn(page("무시될제목"));

        // when
        TelegramCollectionOutcome outcome = collector.collect(channel);

        // then: 페이지 안에서 가장 큰 messageId(103)가 다음 커서, 빈 페이지에서 종료
        assertThat(outcome.posts()).extracting(post -> post.messageId()).containsExactly(101L, 103L, 102L, 104L);
        assertThat(outcome.channelMeta().title()).isEqualTo("첫페이지제목");
        assertThat(outcome.channelMeta().photoUrl()).isEqualTo("https://photo/첫페이지제목");
        verify(previewClient, times(3)).fetchPage(eq(HANDLE), any(), isNull());
    }

    @Test
    @DisplayName("[증분 수집에서 새 글이 없으면 빈 목록이지만 채널 메타는 받아 둔다]")
    void collect_incremental_noNewPosts_stillReturnsMeta() {
        given(telegramPostReader.findLastMessageId(channel)).willReturn(Optional.of(500L));
        given(previewClient.fetchPage(HANDLE, 500L, null)).willReturn(page("제목"));

        TelegramCollectionOutcome outcome = collector.collect(channel);

        assertThat(outcome.posts()).isEmpty();
        assertThat(outcome.channelMeta().title()).isEqualTo("제목");
    }

    @Test
    @DisplayName("[증분 수집은 최대 5페이지에서 멈춘다 - 글이 계속 나와도 한 번의 실행이 무한히 이어지지 않는다]")
    void collect_incremental_stopsAtMaxPages() {
        LocalDateTime now = LocalDateTime.now();
        given(telegramPostReader.findLastMessageId(channel)).willReturn(Optional.of(0L));
        for (long cursor = 0; cursor < 5; cursor++) {
            given(previewClient.fetchPage(HANDLE, cursor, null)).willReturn(page("제목", message(cursor + 1, now)));
        }

        TelegramCollectionOutcome outcome = collector.collect(channel);

        assertThat(outcome.posts()).hasSize(5);
        verify(previewClient, times(5)).fetchPage(eq(HANDLE), any(), isNull());
        verify(previewClient, never()).fetchPage(HANDLE, 5L, null);
    }

    @Test
    @DisplayName("[저장된 글이 없으면 최초 수집: 최신 페이지부터 가장 작은 messageId 이전으로 과거 방향으로 내려간다]")
    void collect_initial_walksBackwardByMinMessageId() {
        LocalDateTime now = LocalDateTime.now();
        given(telegramPostReader.findLastMessageId(channel)).willReturn(Optional.empty());
        given(previewClient.fetchPage(HANDLE, null, null))
            .willReturn(page("제목", message(200, now), message(190, now), message(195, now)));
        given(previewClient.fetchPage(HANDLE, null, 190L)).willReturn(page("무시", message(180, now)));
        given(previewClient.fetchPage(HANDLE, null, 180L)).willReturn(page("무시"));

        TelegramCollectionOutcome outcome = collector.collect(channel);

        assertThat(outcome.posts()).extracting(post -> post.messageId()).containsExactly(200L, 190L, 195L, 180L);
        assertThat(outcome.channelMeta().title()).isEqualTo("제목");
    }

    @Test
    @DisplayName("[최초 수집은 보존기간(14일)을 넘긴 글이 나오는 페이지에서 멈춘다 - 더 파봐야 전부 하드필터에 걸린다]")
    void collect_initial_stopsWhenRetentionCutoffReached() {
        LocalDateTime now = LocalDateTime.now();
        given(telegramPostReader.findLastMessageId(channel)).willReturn(Optional.empty());
        given(previewClient.fetchPage(HANDLE, null, null))
            .willReturn(page("제목", message(300, now), message(299, now.minusDays(15))));

        TelegramCollectionOutcome outcome = collector.collect(channel);

        assertThat(outcome.posts()).hasSize(2);
        verify(previewClient, times(1)).fetchPage(eq(HANDLE), any(), any());
    }

    @Test
    @DisplayName("[최초 수집은 최대 4페이지에서 멈춘다]")
    void collect_initial_stopsAtMaxPages() {
        LocalDateTime now = LocalDateTime.now();
        given(telegramPostReader.findLastMessageId(channel)).willReturn(Optional.empty());
        given(previewClient.fetchPage(HANDLE, null, null)).willReturn(page("제목", message(100, now)));
        given(previewClient.fetchPage(HANDLE, null, 100L)).willReturn(page("제목", message(90, now)));
        given(previewClient.fetchPage(HANDLE, null, 90L)).willReturn(page("제목", message(80, now)));
        given(previewClient.fetchPage(HANDLE, null, 80L)).willReturn(page("제목", message(70, now)));

        TelegramCollectionOutcome outcome = collector.collect(channel);

        assertThat(outcome.posts()).hasSize(4);
        verify(previewClient, never()).fetchPage(HANDLE, null, 70L);
    }

    @Test
    @DisplayName("[최초 수집에서 첫 페이지가 비어 있으면 빈 결과다]")
    void collect_initial_emptyFirstPage() {
        given(telegramPostReader.findLastMessageId(channel)).willReturn(Optional.empty());
        given(previewClient.fetchPage(HANDLE, null, null)).willReturn(page("제목"));

        assertThat(collector.collect(channel).posts()).isEmpty();
    }
}
