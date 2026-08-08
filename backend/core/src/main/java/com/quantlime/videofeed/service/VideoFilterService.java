package com.quantlime.videofeed.service;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import com.quantlime.videofeed.repository.VideoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DISCOVERED 상태 영상에 채널별 filter_config를 적용해 FILTERED_OUT /
 * PENDING_REVIEW / SELECTED로 분기한다(§2 P2, §5 LLM 비용 방어 1번째
 * 방어선인 max_per_run 포함). 탈락 사유(하드필터 종류/velocity 실측값)를
 * 전부 로그로 남긴다(2026-08-02 추가) - 그 전까지는 DB엔 최종 status만
 * 남고 "왜" 탈락했는지가 어디에도 안 남아, velocity_multiplier 같은 값을
 * 튜닝할 근거(하드필터 대비 velocity 단독 통과율)를 전혀 낼 수 없었다.
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

    // PENDING_REVIEW 재평가는 유튜브 API 재조회(I/O)가 필요해졌다(2026-08-02) -
    // DB에 저장된 view_count는 최초 발견 시점(대부분 발행 직후, 조회수가
    // 거의 0일 때) 스냅샷인데 그걸 그대로 재평가에 쓰면 6시간 유예를 두는
    // 의미 자체가 없어지는 버그가 있었다. I/O와 DB 트랜잭션 경계를 분리하는
    // 이 프로젝트의 원칙(ChannelSeedInitializer 등 참고)에 따라, 이 메서드는
    // 재평가 대상 조회(읽기전용)만 하고 실제 API 호출은 FeedCollectionFacade가,
    // 결과 반영+재분류는 아래 reevaluatePendingReview(channel, ids, freshViewCounts)가
    // 담당한다.
    @Transactional(readOnly = true)
    public List<Video> findReevaluationCandidates(Channel channel) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(VELOCITY_GRACE_HOURS);
        return videoRepository.findByStatusAndPublishedAtBefore(VideoStatus.PENDING_REVIEW, cutoff)
            .stream()
            .filter(video -> video.getChannel().getId().equals(channel.getId()))
            .toList();
    }

    // candidateVideoIds를 findReevaluationCandidates()가 반환한(읽기전용
    // 트랜잭션 종료로 detach된) Video 목록에서 그대로 재사용하지 않고 ID만
    // 받아 이 트랜잭션 안에서 다시 조회한다 - detached 엔티티를 그대로
    // mutate하면 저장이 안 되는 함정(ChannelVelocityInitializationService의
    // self-invocation 버그와 증상이 같음)을 피하기 위함. freshViewCounts에
    // 없는 영상(삭제/비공개 등 API가 값을 못 준 경우)은 기존 view_count를
    // 그대로 쓴다 - 영구히 PENDING_REVIEW로 남기지 않고 이번 사이클에
    // 어떻게든 재분류를 확정짓기 위함.
    @Transactional
    public void reevaluatePendingReview(Channel channel, List<Long> candidateVideoIds,
                                         Map<String, Long> freshViewCountsByExternalId) {
        if (candidateVideoIds.isEmpty()) {
            return;
        }
        LocalDateTime checkedAt = LocalDateTime.now();
        List<Video> videos = videoRepository.findAllById(candidateVideoIds);
        videos.forEach(video -> {
            Long freshViewCount = freshViewCountsByExternalId.get(video.getExternalVideoId());
            if (freshViewCount != null) {
                video.updateViewCount(freshViewCount, checkedAt);
            }
        });
        classifyAndSelect(channel, videos);
    }

    private void classifyAndSelect(Channel channel, List<Video> candidates) {
        ChannelFilterConfig config = channel.getFilterConfig();
        List<Video> eligible = candidates.stream()
            .filter(video -> classify(video, channel, config))
            .toList();
        selectUpToMaxPerRun(channel, eligible, config.maxPerRun());
    }

    /**
     * @return true면 SELECTED 후보(max_per_run 컷 대상), false면 이미
     * FILTERED_OUT/PENDING_REVIEW로 상태가 확정된 것
     */
    private boolean classify(Video video, Channel channel, ChannelFilterConfig config) {
        String hardFilterReason = hardFilterRejectionReason(video, config);
        if (hardFilterReason != null) {
            video.markFilteredOut();
            log.info("영상 탈락(하드필터): videoId={}, channel={}, reason={}, title={}",
                video.getId(), channel.getName(), hardFilterReason, video.getTitle());
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
        return classifyByVelocity(video, channel, config, hoursSincePublish);
    }

    private String hardFilterRejectionReason(Video video, ChannelFilterConfig config) {
        if (video.getDurationSec() != null && video.getDurationSec() < config.minDurationSec()) {
            return "MIN_DURATION(durationSec=%d < %d)".formatted(video.getDurationSec(), config.minDurationSec());
        }
        String title = video.getTitle().toLowerCase(Locale.KOREAN);
        String excludedKeyword = config.titleExclude().stream()
            .filter(keyword -> title.contains(keyword.toLowerCase(Locale.KOREAN)))
            .findFirst()
            .orElse(null);
        if (excludedKeyword != null) {
            return "TITLE_EXCLUDE(keyword=%s)".formatted(excludedKeyword);
        }
        if (!config.titleInclude().isEmpty()
            && config.titleInclude().stream().noneMatch(keyword -> title.contains(keyword.toLowerCase(Locale.KOREAN)))) {
            return "TITLE_INCLUDE_MISSING";
        }
        return null;
    }

    private boolean classifyByVelocity(Video video, Channel channel, ChannelFilterConfig config, long hoursSincePublish) {
        if (channel.getMedianVelocity() == null) {
            log.warn("median_velocity 미산정 채널 - velocity 검사 없이 통과: channel={}", channel.getName());
            return true;
        }
        long viewCount = video.getViewCount() != null ? video.getViewCount() : 0L;
        BigDecimal velocity = BigDecimal.valueOf(viewCount)
            .divide(BigDecimal.valueOf(Math.max(hoursSincePublish, 1)), 4, RoundingMode.HALF_UP);
        BigDecimal threshold = channel.getMedianVelocity().multiply(BigDecimal.valueOf(config.velocityMultiplier()));
        if (velocity.compareTo(threshold) >= 0) {
            return true;
        }
        video.markFilteredOut();
        log.info("영상 탈락(velocity 미달): videoId={}, channel={}, velocity={}, threshold={}, "
                + "viewCount={}, hoursSincePublish={}, title={}",
            video.getId(), channel.getName(), velocity, threshold, viewCount, hoursSincePublish, video.getTitle());
        return false;
    }

    // max_per_run은 "이번 수집 사이클에서 몇 개를 볼지"가 아니라 "영상 발행일
    // 기준 하루에 몇 개까지 선택할지"다 - 사이클 단위로 적용하면 수집이 며칠
    // 걸러 뛴 뒤 한 번에 몰아서 돌 때(로컬 개발 환경에서 흔함) 그 며칠치
    // 백로그 전체가 단 한 번의 상한만 적용받아 대부분 영구 탈락해버린다.
    // 발행일별로 후보를 나눠 각 날짜마다 독립적으로 상한을 적용한다.
    private void selectUpToMaxPerRun(Channel channel, List<Video> eligible, int maxPerRun) {
        Map<LocalDate, List<Video>> byPublishedDate = eligible.stream()
            .collect(Collectors.groupingBy(video -> video.getPublishedAt().toLocalDate()));
        byPublishedDate.forEach((publishedDate, videosOnDate) ->
            selectUpToDailyQuota(channel, videosOnDate, maxPerRun, publishedDate));
    }

    // 같은 날짜의 후보가 여러 수집 사이클에 걸쳐 나뉘어 들어올 수 있어(예:
    // 오전 사이클에서 일부, 오후 사이클에서 나머지 신규 발견), 이번 배치의
    // 후보 개수만 보지 않고 그 날짜에 이미 SELECTED된 개수를 DB에서 다시 세어
    // 남은 쿼터만 적용한다 - 그래야 하루 상한이 사이클 횟수와 무관하게 지켜진다.
    private void selectUpToDailyQuota(Channel channel, List<Video> videosOnDate, int maxPerRun, LocalDate publishedDate) {
        int alreadySelected = videoRepository.countByChannelAndStatusAndPublishedAtBetween(
            channel, VideoStatus.SELECTED, publishedDate.atStartOfDay(), publishedDate.plusDays(1).atStartOfDay());
        int remainingQuota = Math.max(maxPerRun - alreadySelected, 0);

        List<Video> ranked = videosOnDate.stream()
            .sorted(Comparator.comparing((Video v) -> v.getViewCount() != null ? v.getViewCount() : 0L).reversed())
            .toList();
        for (int i = 0; i < ranked.size(); i++) {
            if (i < remainingQuota) {
                ranked.get(i).markSelected();
            } else {
                ranked.get(i).markFilteredOut();
                log.info("영상 탈락(하루 max_per_run 컷): videoId={}, channel={}, publishedDate={}, title={}",
                    ranked.get(i).getId(), channel.getName(), publishedDate, ranked.get(i).getTitle());
            }
        }
    }
}
