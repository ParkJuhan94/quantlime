package com.quantlime.telegramfeed.service;

import com.quantlime.telegramfeed.dto.CollectedTelegramPost;
import com.quantlime.telegramfeed.dto.TelegramChannelMeta;
import com.quantlime.telegramfeed.dto.TelegramCollectResult;
import com.quantlime.telegramfeed.dto.TelegramCollectionOutcome;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TelegramCollectionFacadeTest {

    @Mock
    private com.quantlime.common.lock.RedisLockService redisLockService;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private TelegramPostCollector telegramPostCollector;

    @Mock
    private TelegramPostPersistService telegramPostPersistService;

    @Mock
    private TelegramPostFilterService telegramPostFilterService;

    @InjectMocks
    private TelegramCollectionFacade telegramCollectionFacade;

    private Channel channelOf(Long id, String handle) {
        Channel channel = Channel.ofTelegram(handle, "테스트 채널", 30,
            new TelegramFilterConfig(300, List.of(), List.of()));
        ReflectionTestUtils.setField(channel, "id", id);
        return channel;
    }

    @Test
    @DisplayName("[Platform.TELEGRAM 채널만 조회해서 수집한다]")
    void runAll_collectsOnlyTelegramChannels() {
        // given
        Channel channel = channelOf(1L, "handle1");
        given(channelRepository.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.TELEGRAM))
            .willReturn(List.of(channel));
        TelegramCollectionOutcome outcome = new TelegramCollectionOutcome(List.of(), null);
        given(telegramPostCollector.collect(channel)).willReturn(outcome);
        given(telegramPostPersistService.upsertAll(channel, outcome.posts())).willReturn(0);
        given(channelRepository.findById(1L)).willReturn(java.util.Optional.of(channel));

        // when
        List<TelegramCollectResult> results = telegramCollectionFacade.runAll();

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
        verify(telegramPostFilterService).applyFilters(channel);
    }

    @Test
    @DisplayName("[한 채널의 수집 실패가 나머지 채널 수집을 막지 않는다(장애 격리)]")
    void runAll_oneChannelFails_isolatesFailureAndContinues() {
        // given
        Channel failingChannel = channelOf(1L, "failing");
        Channel okChannel = channelOf(2L, "ok");
        given(channelRepository.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.TELEGRAM))
            .willReturn(List.of(failingChannel, okChannel));
        given(telegramPostCollector.collect(failingChannel)).willThrow(new RuntimeException("스크래핑 실패"));
        TelegramCollectionOutcome okOutcome = new TelegramCollectionOutcome(
            List.of(new CollectedTelegramPost("ok/1", 1L, "본문", LocalDateTime.now(), 10L, false)),
            new TelegramChannelMeta("OK 채널", "https://cdn/photo.jpg"));
        given(telegramPostCollector.collect(okChannel)).willReturn(okOutcome);
        given(telegramPostPersistService.upsertAll(okChannel, okOutcome.posts())).willReturn(1);
        given(channelRepository.findById(2L)).willReturn(java.util.Optional.of(okChannel));

        // when
        List<TelegramCollectResult> results = telegramCollectionFacade.runAll();

        // then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).channelName()).isEqualTo("테스트 채널");
        assertThat(results.get(1).success()).isTrue();
        assertThat(results.get(1).discoveredCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[프로필 사진 URL이 실제로 바뀐 경우에만 갱신하고, lastCollectedAt은 매번 갱신한다]")
    void updateChannelMeta_updatesPhotoOnlyWhenChanged() {
        // given
        Channel channel = channelOf(1L, "handle1");
        given(channelRepository.findById(1L)).willReturn(java.util.Optional.of(channel));
        TelegramChannelMeta meta = new TelegramChannelMeta("새 채널명", "https://cdn/new-photo.jpg");

        // when
        telegramCollectionFacade.updateChannelMeta(1L, meta);

        // then
        assertThat(channel.getLastCollectedAt()).isNotNull();
        assertThat(channel.getProfileImageUrl()).isEqualTo("https://cdn/new-photo.jpg");
        verify(channelRepository).save(channel);
    }

    @Test
    @DisplayName("[채널 메타 조회에 실패해(null) 사진 URL을 못 받아도 lastCollectedAt은 정상 갱신된다]")
    void updateChannelMeta_nullMeta_stillUpdatesLastCollectedAt() {
        // given
        Channel channel = channelOf(1L, "handle1");
        given(channelRepository.findById(1L)).willReturn(java.util.Optional.of(channel));

        // when
        telegramCollectionFacade.updateChannelMeta(1L, null);

        // then
        assertThat(channel.getLastCollectedAt()).isNotNull();
        assertThat(channel.getProfileImageUrl()).isNull();
        verify(channelRepository).save(channel);
    }
}
