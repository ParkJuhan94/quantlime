package com.quantlime.telegramfeed.service;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.support.DataJpaTestSupport;
import com.quantlime.telegramfeed.domain.TelegramDigest;
import com.quantlime.telegramfeed.domain.TelegramDigestTicker;
import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.dto.TelegramRetentionResult;
import com.quantlime.telegramfeed.repository.TelegramDigestRepository;
import com.quantlime.telegramfeed.repository.TelegramDigestTickerRepository;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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

// VideoRetentionServiceTest와 동일한 이유(실제 여러 테이블에 걸친 삭제 검증)로
// 격리된 Testcontainers MySQL(DataJpaTestSupport)에서 실제 DELETE까지 확인한다.
@Tag("integration")
class TelegramPostRetentionServiceTest extends DataJpaTestSupport {

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private TelegramPostRepository telegramPostRepository;

    @Autowired
    private TelegramDigestRepository telegramDigestRepository;

    @Autowired
    private TelegramDigestTickerRepository telegramDigestTickerRepository;

    @Autowired
    private TestEntityManager entityManager;

    private TelegramPostRetentionService telegramPostRetentionService;

    @BeforeEach
    void setUp() {
        TelegramPostRetentionDeleteService telegramPostRetentionDeleteService = new TelegramPostRetentionDeleteService(
            telegramPostRepository, telegramDigestRepository, telegramDigestTickerRepository);
        telegramPostRetentionService = new TelegramPostRetentionService(
            null, telegramPostRepository, telegramDigestRepository, telegramPostRetentionDeleteService);
    }

    private Channel channelOf(String handle) {
        return channelRepository.save(Channel.ofTelegram(handle, "테스트 채널", 30,
            new TelegramFilterConfig(300, List.of(), List.of())));
    }

    private TelegramPost seedPost(Channel channel, long messageId, LocalDateTime publishedAt) {
        return telegramPostRepository.save(TelegramPost.of(
            channel, channel.getExternalChannelId() + "/" + messageId, messageId, "본문",
            publishedAt, 100L, LocalDateTime.now(), false));
    }

    private TelegramDigest seedDigest(Channel channel, LocalDate digestDate) {
        TelegramDigest digest = telegramDigestRepository.save(TelegramDigest.of(channel, digestDate,
            "gemini-3.5-flash-lite",
            "{\"summary\":\"요약\",\"key_points\":[],\"mentioned_tickers\":[],\"caveat\":\"고지\"}", 100, 50));
        telegramDigestTickerRepository.save(
            TelegramDigestTicker.of(digest, "AAPL", "애플", "BULLISH", BigDecimal.valueOf(0.8)));
        return digest;
    }

    @Test
    @DisplayName("[보존 기간(14일)보다 오래된 글/다이제스트(+태깅종목)를 전부 삭제하고, 최근 데이터는 남긴다]")
    void deleteOlderThanRetention_deletesOldDataAndKeepsRecentData() {
        // given
        Channel channel = channelOf("handle-old");
        TelegramPost oldPost = seedPost(channel, 1L, LocalDateTime.now().minusDays(15));
        TelegramPost recentPost = seedPost(channel, 2L, LocalDateTime.now().minusDays(3));
        TelegramDigest oldDigest = seedDigest(channel, LocalDate.now().minusDays(15));
        TelegramDigest recentDigest = seedDigest(channel, LocalDate.now().minusDays(3));

        // when
        TelegramRetentionResult result = telegramPostRetentionService.deleteOlderThanRetention();
        // deleteAllByIdInBatch는 벌크 DELETE라 영속성 컨텍스트를 자동으로 비우지
        // 않는다(VideoRetentionServiceTest와 동일 이유) - flush+clear로 강제 초기화.
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(result.deletedPostCount()).isEqualTo(1);
        assertThat(result.deletedDigestCount()).isEqualTo(1);
        assertThat(telegramPostRepository.findById(oldPost.getId())).isEmpty();
        assertThat(telegramDigestRepository.findById(oldDigest.getId())).isEmpty();
        assertThat(telegramDigestTickerRepository.findByTelegramDigest_IdIn(List.of(oldDigest.getId()))).isEmpty();

        assertThat(telegramPostRepository.findById(recentPost.getId())).isPresent();
        assertThat(telegramDigestRepository.findById(recentDigest.getId())).isPresent();
        assertThat(telegramDigestTickerRepository.findByTelegramDigest_IdIn(List.of(recentDigest.getId()))).hasSize(1);
    }

    @Test
    @DisplayName("[보존 기간 초과 데이터가 없으면 아무것도 지우지 않고 0을 반환한다]")
    void deleteOlderThanRetention_nothingToDelete_returnsZero() {
        // given
        Channel channel = channelOf("handle-recent");
        seedPost(channel, 1L, LocalDateTime.now().minusDays(1));
        seedDigest(channel, LocalDate.now().minusDays(1));

        // when
        TelegramRetentionResult result = telegramPostRetentionService.deleteOlderThanRetention();

        // then
        assertThat(result.deletedPostCount()).isEqualTo(0);
        assertThat(result.deletedDigestCount()).isEqualTo(0);
        assertThat(telegramPostRepository.count()).isEqualTo(1);
        assertThat(telegramDigestRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("[runExclusively는 락을 획득하면 삭제 결과를 감싼 Optional을 반환한다]")
    void runExclusively_whenLockAcquired_returnsDeletedCount() {
        // given
        RedisLockService redisLockService = mock(RedisLockService.class);
        TelegramPostRetentionDeleteService telegramPostRetentionDeleteService = new TelegramPostRetentionDeleteService(
            telegramPostRepository, telegramDigestRepository, telegramDigestTickerRepository);
        TelegramPostRetentionService serviceWithLock = new TelegramPostRetentionService(
            redisLockService, telegramPostRepository, telegramDigestRepository, telegramPostRetentionDeleteService);
        Channel channel = channelOf("handle-old");
        seedPost(channel, 1L, LocalDateTime.now().minusDays(15));
        given(redisLockService.runExclusively(any(), any(), any())).willAnswer(invocation -> {
            Supplier<TelegramRetentionResult> task = invocation.getArgument(2);
            return Optional.of(task.get());
        });

        // when
        Optional<TelegramRetentionResult> result = serviceWithLock.runExclusively();

        // then
        assertThat(result).isPresent();
        assertThat(result.get().deletedPostCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[runExclusively는 락 획득에 실패하면 정리 작업을 실행하지 않고 빈 Optional을 반환한다]")
    void runExclusively_whenLockNotAcquired_skipsCleanup() {
        // given
        RedisLockService redisLockService = mock(RedisLockService.class);
        TelegramPostRetentionDeleteService telegramPostRetentionDeleteService = new TelegramPostRetentionDeleteService(
            telegramPostRepository, telegramDigestRepository, telegramDigestTickerRepository);
        TelegramPostRetentionService serviceWithLock = new TelegramPostRetentionService(
            redisLockService, telegramPostRepository, telegramDigestRepository, telegramPostRetentionDeleteService);
        Channel channel = channelOf("handle-old");
        seedPost(channel, 1L, LocalDateTime.now().minusDays(15));
        given(redisLockService.runExclusively(any(), any(), any())).willReturn(Optional.empty());

        // when
        Optional<TelegramRetentionResult> result = serviceWithLock.runExclusively();

        // then
        assertThat(result).isEmpty();
        assertThat(telegramPostRepository.count()).isEqualTo(1);
    }
}
