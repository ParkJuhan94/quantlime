package com.quantlime.telegramfeed.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.lock.RedisLockService;
import com.quantlime.telegramfeed.dto.TelegramChannelMeta;
import com.quantlime.telegramfeed.dto.TelegramCollectResult;
import com.quantlime.telegramfeed.dto.TelegramCollectionOutcome;
import com.quantlime.telegramfeed.exception.TelegramFeedErrorCode;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채널별 수집→적재→필터링을 오케스트레이션한다(FeedCollectionFacade와
 * 동일 구조). 한 채널의 실패가 나머지 채널 수집을 막지 않도록 채널 단위로
 * 장애를 격리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramCollectionFacade {

    private static final String LOCK_KEY = "lock:telegram-collect";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final RedisLockService redisLockService;
    private final ChannelRepository channelRepository;
    private final TelegramPostCollector telegramPostCollector;
    private final TelegramPostPersistService telegramPostPersistService;
    private final TelegramPostFilterService telegramPostFilterService;

    public Optional<List<TelegramCollectResult>> runAllExclusively() {
        return redisLockService.runExclusively(LOCK_KEY, LOCK_TTL, this::runAll);
    }

    public List<TelegramCollectResult> runAll() {
        // Platform.TELEGRAM으로 한정 - FeedCollectionFacade가 P7-0에서
        // Platform.YOUTUBE로 한정한 것과 대칭. 이 파사드는 TelegramPostCollector만
        // 쓰므로 유튜브 채널이 섞여 들어오면 안 된다.
        List<Channel> channels = channelRepository.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.TELEGRAM);
        List<TelegramCollectResult> results = new ArrayList<>();
        for (Channel channel : channels) {
            try {
                results.add(collectChannel(channel));
            } catch (Exception e) {
                log.error("텔레그램 채널 수집 실패: channel={}, reason={}", channel.getName(), e.getMessage(), e);
                results.add(TelegramCollectResult.failed(channel.getName(), e.getMessage()));
            }
        }
        return results;
    }

    private TelegramCollectResult collectChannel(Channel channel) {
        TelegramCollectionOutcome outcome = telegramPostCollector.collect(channel);
        int insertedCount = telegramPostPersistService.upsertAll(channel, outcome.posts());
        telegramPostFilterService.applyFilters(channel);
        updateChannelMeta(channel.getId(), outcome.channelMeta());
        return TelegramCollectResult.success(channel.getName(), insertedCount);
    }

    // updateChannelMeta(Long, TelegramChannelMeta)가 collectChannel -> runAll
    // -> runAllExclusively로 self-invocation되는 경로뿐이라 이 메서드의
    // @Transactional은 프록시를 안 타 무시된다(FeedCollectionFacade
    // .updateLastCollectedAt과 동일 원인) - findById는 내장 메서드라 그
    // 자체로 트랜잭셔널이지만 반환된 엔티티는 곧바로 detached되므로 save()를
    // 명시적으로 호출해야 변경분이 실제로 반영된다. 프로필 사진은 값이 없을
    // 때만 채우는 유튜브식 백필이 아니라 매 수집마다 갱신한다 - telesco.pe
    // CDN URL이 회전될 수 있는데 "없을 때만"이면 만료된 URL이 영구히
    // 깨진 채로 남기 때문(값이 실제로 바뀐 경우에만 write).
    @Transactional
    void updateChannelMeta(Long channelId, TelegramChannelMeta meta) {
        Channel channel = channelRepository.findById(channelId)
            .orElseThrow(() -> new NotFoundException(TelegramFeedErrorCode.NOT_FOUND_CHANNEL));
        channel.updateLastCollectedAt(LocalDateTime.now());
        if (meta != null && meta.photoUrl() != null && !meta.photoUrl().equals(channel.getProfileImageUrl())) {
            channel.updateProfileImageUrl(meta.photoUrl());
        }
        channelRepository.save(channel);
    }
}
