package com.quantlime.videofeed.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.lock.RedisLockService;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.dto.CollectResult;
import com.quantlime.videofeed.dto.CollectedVideo;
import com.quantlime.videofeed.exception.VideoFeedErrorCode;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채널별 수집→적재→필터링을 오케스트레이션한다. 한 채널의 실패가 나머지
 * 채널 수집을 막지 않도록 채널 단위로 장애를 격리한다(§5 장애 격리 규칙).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedCollectionFacade {

    private static final String LOCK_KEY = "lock:feed-collect";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final RedisLockService redisLockService;
    private final ChannelRepository channelRepository;
    private final YoutubeVideoCollector youtubeVideoCollector;
    private final VideoPersistService videoPersistService;
    private final VideoFilterService videoFilterService;

    /**
     * 락을 잡은 채로 runAll() + reevaluatePendingReview()를 한 번에 실행한다
     * (원래 FeedCollectionScheduler가 두 단계를 순서대로 부르던 것을 그대로
     * 유지 - 두 단계 사이에 다른 실행이 끼어들지 못하게 하나의 락 구간으로
     * 묶는다). FeedCollectionScheduler·FeedCollectionAdminController(수동
     * 트리거) 둘 다 이 진입점만 호출해야 서로 중복 실행을 막을 수 있다.
     */
    public Optional<List<CollectResult>> runAllExclusively() {
        return redisLockService.runExclusively(LOCK_KEY, LOCK_TTL, () -> {
            List<CollectResult> results = runAll();
            reevaluatePendingReview();
            return results;
        });
    }

    public List<CollectResult> runAll() {
        List<Channel> channels = channelRepository.findByEnabledTrueOrderByPriorityAsc();
        List<CollectResult> results = new ArrayList<>();
        for (Channel channel : channels) {
            try {
                results.add(collectChannel(channel));
            } catch (Exception e) {
                log.error("채널 수집 실패: channel={}, reason={}", channel.getName(), e.getMessage(), e);
                results.add(CollectResult.failed(channel.getName(), e.getMessage()));
            }
        }
        return results;
    }

    public void reevaluatePendingReview() {
        List<Channel> channels = channelRepository.findByEnabledTrueOrderByPriorityAsc();
        for (Channel channel : channels) {
            try {
                reevaluateChannelPendingReview(channel);
            } catch (Exception e) {
                log.error("PENDING_REVIEW 재평가 실패: channel={}, reason={}",
                    channel.getName(), e.getMessage(), e);
            }
        }
    }

    // 재평가 대상 조회(읽기전용 트랜잭션)와 실제 반영(쓰기 트랜잭션) 사이에
    // 유튜브 API 재조회(I/O)를 끼워 최신 조회수로 velocity를 판정한다
    // (2026-08-02 - VideoFilterService 주석 참고. 이전엔 최초 발견 시점의
    // 낡은 view_count로 재평가해 6시간 유예가 무의미해지는 버그가 있었음).
    private void reevaluateChannelPendingReview(Channel channel) {
        List<Video> candidates = videoFilterService.findReevaluationCandidates(channel);
        if (candidates.isEmpty()) {
            return;
        }
        List<String> externalVideoIds = candidates.stream().map(Video::getExternalVideoId).toList();
        Map<String, Long> freshViewCounts = youtubeVideoCollector.fetchViewCounts(externalVideoIds);
        List<Long> candidateVideoIds = candidates.stream().map(Video::getId).toList();
        videoFilterService.reevaluatePendingReview(channel, candidateVideoIds, freshViewCounts);
    }

    private CollectResult collectChannel(Channel channel) {
        List<CollectedVideo> collected = youtubeVideoCollector.collect(channel);
        int insertedCount = videoPersistService.upsertAll(channel, collected);
        videoFilterService.applyFilters(channel);
        updateLastCollectedAt(channel.getId());
        return CollectResult.success(channel.getName(), insertedCount);
    }

    @Transactional
    void updateLastCollectedAt(Long channelId) {
        Channel channel = channelRepository.findById(channelId)
            .orElseThrow(() -> new NotFoundException(VideoFeedErrorCode.NOT_FOUND_CHANNEL));
        channel.updateLastCollectedAt(LocalDateTime.now());
    }
}
