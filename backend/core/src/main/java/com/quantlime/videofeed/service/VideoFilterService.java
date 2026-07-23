package com.quantlime.videofeed.service;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import com.quantlime.videofeed.repository.VideoRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DISCOVERED 상태 영상에 채널별 filter_config를 적용해 FILTERED_OUT /
 * PENDING_REVIEW / SELECTED로 분기한다(§2 P2, §5 LLM 비용 방어 1번째
 * 방어선인 max_per_run 포함).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoFilterService {

    // 업로드 직후에는 조회수가 아직 안 쌓여 velocity 판정이 무의미하다 -
    // 이 유예 기간 동안은 PENDING_REVIEW로 두고 이후 재평가한다(§7 리스크).
    private static final long VELOCITY_GRACE_HOURS = 6;

    private final VideoRepository videoRepository;

    @Transactional
    public void applyFilters(Channel channel) {
        List<Video> discovered = videoRepository.findByChannelAndStatus(channel, VideoStatus.DISCOVERED);
        classifyAndSelect(channel, discovered);
    }

    @Transactional
    public void reevaluatePendingReview(Channel channel) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(VELOCITY_GRACE_HOURS);
        List<Video> pending = videoRepository.findByStatusAndPublishedAtBefore(VideoStatus.PENDING_REVIEW, cutoff)
            .stream()
            .filter(video -> video.getChannel().getId().equals(channel.getId()))
            .toList();
        classifyAndSelect(channel, pending);
    }

    private void classifyAndSelect(Channel channel, List<Video> candidates) {
        ChannelFilterConfig config = channel.getFilterConfig();
        List<Video> eligible = candidates.stream()
            .filter(video -> classify(video, channel, config))
            .toList();
        selectUpToMaxPerRun(eligible, config.maxPerRun());
    }

    /**
     * @return true면 SELECTED 후보(max_per_run 컷 대상), false면 이미
     * FILTERED_OUT/PENDING_REVIEW로 상태가 확정된 것
     */
    private boolean classify(Video video, Channel channel, ChannelFilterConfig config) {
        if (failsHardFilters(video, config)) {
            video.markFilteredOut();
            return false;
        }
        if (config.velocityMultiplier() <= 0) {
            return true;
        }
        long hoursSincePublish = Duration.between(video.getPublishedAt(), LocalDateTime.now()).toHours();
        if (hoursSincePublish < VELOCITY_GRACE_HOURS) {
            video.markPendingReview();
            return false;
        }
        if (passesVelocity(video, channel, config, hoursSincePublish)) {
            return true;
        }
        video.markFilteredOut();
        return false;
    }

    private boolean failsHardFilters(Video video, ChannelFilterConfig config) {
        if (video.getDurationSec() != null && video.getDurationSec() < config.minDurationSec()) {
            return true;
        }
        String title = video.getTitle().toLowerCase(Locale.KOREAN);
        boolean excluded = config.titleExclude().stream()
            .anyMatch(keyword -> title.contains(keyword.toLowerCase(Locale.KOREAN)));
        if (excluded) {
            return true;
        }
        if (!config.titleInclude().isEmpty()) {
            return config.titleInclude().stream()
                .noneMatch(keyword -> title.contains(keyword.toLowerCase(Locale.KOREAN)));
        }
        return false;
    }

    private boolean passesVelocity(Video video, Channel channel, ChannelFilterConfig config, long hoursSincePublish) {
        if (channel.getMedianVelocity() == null) {
            log.warn("median_velocity 미산정 채널 - velocity 검사 없이 통과: channel={}", channel.getName());
            return true;
        }
        long viewCount = video.getViewCount() != null ? video.getViewCount() : 0L;
        BigDecimal velocity = BigDecimal.valueOf(viewCount)
            .divide(BigDecimal.valueOf(Math.max(hoursSincePublish, 1)), 4, java.math.RoundingMode.HALF_UP);
        BigDecimal threshold = channel.getMedianVelocity()
            .multiply(BigDecimal.valueOf(config.velocityMultiplier()));
        return velocity.compareTo(threshold) >= 0;
    }

    private void selectUpToMaxPerRun(List<Video> eligible, int maxPerRun) {
        List<Video> ranked = eligible.stream()
            .sorted(Comparator.comparing((Video v) -> v.getViewCount() != null ? v.getViewCount() : 0L).reversed())
            .collect(Collectors.toList());
        for (int i = 0; i < ranked.size(); i++) {
            if (i < maxPerRun) {
                ranked.get(i).markSelected();
            } else {
                ranked.get(i).markFilteredOut();
            }
        }
    }
}
