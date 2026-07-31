package com.quantlime.videofeed.service;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.videofeed.repository.SummaryRepository;
import com.quantlime.videofeed.repository.TranscriptRepository;
import com.quantlime.videofeed.repository.VideoRepository;
import com.quantlime.videofeed.repository.VideoTickerRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * P6(프론트 영상 요약 피드)가 최근 RETENTION_DAYS일치만 조회하도록 제한된
 * 뒤(VideoFeedPage), 그 범위를 벗어난 영상 데이터를 DB에 무기한 쌓아둘
 * 이유가 없어 신설(2026-07-31) - 프론트 조회 가능 범위와 반드시 같은 값을
 * 유지해야 한다(BE/FE 간 공유 설정 파일이 없는 프로젝트라 각자 상수로 둠 -
 * frontend/src/utils/dateFilter.ts의 VIDEO_FEED_RETENTION_DAYS 참고).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoRetentionService {

    private static final int RETENTION_DAYS = 10;
    private static final String LOCK_KEY = "lock:video-retention-cleanup";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final RedisLockService redisLockService;
    private final VideoRepository videoRepository;
    private final TranscriptRepository transcriptRepository;
    private final SummaryRepository summaryRepository;
    private final VideoTickerRepository videoTickerRepository;

    /**
     * 락을 잡은 채로 deleteVideosOlderThanRetention()을 실행한다.
     * VideoRetentionScheduler·FeedCollectionAdminController(수동 트리거)
     * 둘 다 이 진입점만 호출해야 동시에 같은 행을 지우려는 중복 실행을
     * 막을 수 있다(SummaryCollectionFacade.runBatchExclusively와 동일 패턴).
     */
    public Optional<Integer> runExclusively() {
        return redisLockService.runExclusively(LOCK_KEY, LOCK_TTL, this::deleteVideosOlderThanRetention);
    }

    // this::deleteVideosOlderThanRetention로 self-invocation되는 경로(위
    // runExclusively)가 있어 여기에 @Transactional을 붙이면 프록시를 안 타
    // 조용히 무시된다 - 대신 아래 4개 delete 호출은 각자 Spring Data
    // 리포지토리 프록시를 통해 독립적으로 트랜잭셔널하므로, 중간에 실패해도
    // 다음 실행 때 남은 대상만 다시 잡혀 자연히 복구된다(전량 원자성은
    // 포기하되 고아 행이 영구히 남지는 않음).
    public int deleteVideosOlderThanRetention() {
        LocalDateTime cutoff = LocalDate.now().minusDays(RETENTION_DAYS).atStartOfDay();
        List<Long> videoIds = videoRepository.findIdsByPublishedAtBefore(cutoff);
        if (videoIds.isEmpty()) {
            log.info("보존 기간({}일) 초과 영상 없음 - 삭제할 데이터 없음", RETENTION_DAYS);
            return 0;
        }

        // video_id를 NO_CONSTRAINT FK로만 참조해(§9.2 관례) DB 캐스케이드가
        // 없다 - 자식 테이블부터 먼저 지워야 고아 행이 안 남는다
        // (FeedService.deletePost와 동일한 패턴).
        videoTickerRepository.deleteByVideo_IdIn(videoIds);
        summaryRepository.deleteByVideo_IdIn(videoIds);
        transcriptRepository.deleteByVideo_IdIn(videoIds);
        videoRepository.deleteAllByIdInBatch(videoIds);

        log.info("보존 기간({}일) 초과 영상 삭제 완료: 삭제건수={}, cutoff={}", RETENTION_DAYS, videoIds.size(), cutoff);
        return videoIds.size();
    }
}
