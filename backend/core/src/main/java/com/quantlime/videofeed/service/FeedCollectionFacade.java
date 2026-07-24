package com.quantlime.videofeed.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.dto.CollectResult;
import com.quantlime.videofeed.dto.CollectedVideo;
import com.quantlime.videofeed.exception.VideoFeedErrorCode;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    private final ChannelRepository channelRepository;
    private final YoutubeVideoCollector youtubeVideoCollector;
    private final VideoPersistService videoPersistService;
    private final VideoFilterService videoFilterService;

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
                videoFilterService.reevaluatePendingReview(channel);
            } catch (Exception e) {
                log.error("PENDING_REVIEW 재평가 실패: channel={}, reason={}",
                    channel.getName(), e.getMessage(), e);
            }
        }
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
