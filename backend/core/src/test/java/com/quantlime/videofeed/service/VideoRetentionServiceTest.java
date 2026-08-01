package com.quantlime.videofeed.service;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.support.DataJpaTestSupport;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.Summary;
import com.quantlime.videofeed.domain.Transcript;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoTicker;
import com.quantlime.videofeed.repository.ChannelRepository;
import com.quantlime.videofeed.repository.SummaryRepository;
import com.quantlime.videofeed.repository.TranscriptRepository;
import com.quantlime.videofeed.repository.VideoRepository;
import com.quantlime.videofeed.repository.VideoTickerRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

// 실제로 여러 테이블(video/transcript/summary/video_ticker)에 걸쳐 삭제가
// 정확히 일어나는지(고아 행이 안 남는지) 검증해야 해 Mockito 단위 테스트로는
// 충분하지 않다 - 격리된 Testcontainers MySQL(DataJpaTestSupport)에서
// 실제 DELETE까지 확인한다. 절대 로컬 실 개발 DB를 대상으로 이 로직을
// 수동 실행하지 않는다(전역 CLAUDE.md "DB/공유 상태를 건드리는 실험 전
// 확인 원칙" 참고 - 이 테스트가 그 확인을 대신한다).
@Tag("integration")
class VideoRetentionServiceTest extends DataJpaTestSupport {

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private TranscriptRepository transcriptRepository;

    @Autowired
    private SummaryRepository summaryRepository;

    @Autowired
    private VideoTickerRepository videoTickerRepository;

    @Autowired
    private TestEntityManager entityManager;

    private VideoRetentionService videoRetentionService;

    @BeforeEach
    void setUp() {
        // @DataJpaTest 슬라이스엔 Redis 빈이 없다 - 이 테스트는 락 없는
        // deleteVideosOlderThanRetention()만 직접 호출하고 runExclusively()는
        // 쓰지 않으므로 redisLockService는 실제로 참조되지 않는다.
        videoRetentionService = new VideoRetentionService(
            null, videoRepository, transcriptRepository, summaryRepository, videoTickerRepository);
    }

    private Video seedVideo(String externalVideoId, LocalDateTime publishedAt) {
        Channel channel = channelRepository.save(Channel.of(Platform.YOUTUBE, "UC" + externalVideoId, "UU" + externalVideoId,
            "테스트 채널", 10, new ChannelFilterConfig(180, 1.5, 5, List.of(), List.of())));
        Video video = videoRepository.save(Video.of(
            channel, externalVideoId, "제목", publishedAt, 300, 100L, LocalDateTime.now()));
        transcriptRepository.save(Transcript.of(video, "youtube_auto_caption", "ko", "자막", 2));
        summaryRepository.save(Summary.of(video, "gemini-3.5-flash-lite",
            "{\"summary\":\"요약\",\"key_points\":[],\"mentioned_tickers\":[],\"caveat\":\"고지\"}", 100, 50));
        videoTickerRepository.save(VideoTicker.of(video, "005930", "삼성전자", "BULLISH", BigDecimal.valueOf(0.8)));
        return video;
    }

    @Test
    @DisplayName("[보존 기간(14일)보다 오래된 영상과 그 자식 데이터(자막/요약/태깅종목)를 전부 삭제하고, 최근 영상은 남긴다]")
    void deleteVideosOlderThanRetention_deletesOldVideoAndChildRows_keepsRecentVideo() {
        // given
        Video oldVideo = seedVideo("vid-old", LocalDateTime.now().minusDays(15));
        Video recentVideo = seedVideo("vid-recent", LocalDateTime.now().minusDays(3));

        // when
        int deletedCount = videoRetentionService.deleteVideosOlderThanRetention();
        // deleteAllByIdInBatch는 벌크 DELETE라 영속성 컨텍스트(1차 캐시)를
        // 자동으로 비우지 않는다 - seedVideo에서 로드해둔 oldVideo가 여전히
        // 캐시에 남아 findById가 DB 대신 캐시를 그대로 반환할 수 있어
        // flush+clear로 강제 초기화한 뒤 검증한다.
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(deletedCount).isEqualTo(1);
        assertThat(videoRepository.findById(oldVideo.getId())).isEmpty();
        assertThat(transcriptRepository.findByVideo(oldVideo)).isEmpty();
        assertThat(summaryRepository.findByVideo(oldVideo)).isEmpty();
        assertThat(videoTickerRepository.findByVideo(oldVideo)).isEmpty();

        assertThat(videoRepository.findById(recentVideo.getId())).isPresent();
        assertThat(transcriptRepository.findByVideo(recentVideo)).isPresent();
    }

    @Test
    @DisplayName("[보존 기간 초과 영상이 없으면 아무것도 지우지 않고 0을 반환한다]")
    void deleteVideosOlderThanRetention_nothingToDelete_returnsZero() {
        // given
        seedVideo("vid-recent", LocalDateTime.now().minusDays(1));

        // when
        int deletedCount = videoRetentionService.deleteVideosOlderThanRetention();

        // then
        assertThat(deletedCount).isEqualTo(0);
        assertThat(videoRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("[runExclusively는 락을 획득하면 삭제건수를 감싼 Optional을 반환한다]")
    void runExclusively_whenLockAcquired_returnsDeletedCount() {
        // given
        RedisLockService redisLockService = mock(RedisLockService.class);
        VideoRetentionService serviceWithLock = new VideoRetentionService(
            redisLockService, videoRepository, transcriptRepository, summaryRepository, videoTickerRepository);
        seedVideo("vid-old", LocalDateTime.now().minusDays(15));
        given(redisLockService.runExclusively(any(), any(), any())).willAnswer(invocation -> {
            Supplier<Integer> task = invocation.getArgument(2);
            return Optional.of(task.get());
        });

        // when
        Optional<Integer> result = serviceWithLock.runExclusively();

        // then
        assertThat(result).contains(1);
    }

    @Test
    @DisplayName("[runExclusively는 락 획득에 실패하면 정리 작업을 실행하지 않고 빈 Optional을 반환한다]")
    void runExclusively_whenLockNotAcquired_skipsCleanup() {
        // given
        RedisLockService redisLockService = mock(RedisLockService.class);
        VideoRetentionService serviceWithLock = new VideoRetentionService(
            redisLockService, videoRepository, transcriptRepository, summaryRepository, videoTickerRepository);
        seedVideo("vid-old", LocalDateTime.now().minusDays(15));
        given(redisLockService.runExclusively(any(), any(), any())).willReturn(Optional.empty());

        // when
        Optional<Integer> result = serviceWithLock.runExclusively();

        // then
        assertThat(result).isEmpty();
        assertThat(videoRepository.count()).isEqualTo(1);
    }
}
