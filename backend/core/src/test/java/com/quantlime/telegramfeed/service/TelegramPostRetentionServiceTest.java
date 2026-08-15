package com.quantlime.telegramfeed.service;

import com.quantlime.common.lock.RedisLockService;
import com.quantlime.support.DataJpaTestSupport;
import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostTicker;
import com.quantlime.telegramfeed.domain.TelegramSummary;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.telegramfeed.repository.TelegramPostTickerRepository;
import com.quantlime.telegramfeed.repository.TelegramSummaryRepository;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
import com.quantlime.videofeed.repository.ChannelRepository;
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

// VideoRetentionServiceTest와 동일한 이유(실제 여러 테이블에 걸친 삭제 검증)로
// 격리된 Testcontainers MySQL(DataJpaTestSupport)에서 실제 DELETE까지 확인한다.
@Tag("integration")
class TelegramPostRetentionServiceTest extends DataJpaTestSupport {

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private TelegramPostRepository telegramPostRepository;

    @Autowired
    private TelegramSummaryRepository telegramSummaryRepository;

    @Autowired
    private TelegramPostTickerRepository telegramPostTickerRepository;

    @Autowired
    private TestEntityManager entityManager;

    private TelegramPostRetentionService telegramPostRetentionService;

    @BeforeEach
    void setUp() {
        TelegramPostRetentionDeleteService telegramPostRetentionDeleteService = new TelegramPostRetentionDeleteService(
            telegramPostRepository, telegramSummaryRepository, telegramPostTickerRepository);
        telegramPostRetentionService = new TelegramPostRetentionService(
            null, telegramPostRepository, telegramPostRetentionDeleteService);
    }

    private TelegramPost seedPost(String handle, long messageId, LocalDateTime publishedAt) {
        Channel channel = channelRepository.save(Channel.ofTelegram(handle, "테스트 채널", 30,
            new TelegramFilterConfig(300, 2, List.of(), List.of())));
        TelegramPost post = telegramPostRepository.save(TelegramPost.of(
            channel, handle + "/" + messageId, messageId, "본문", publishedAt, 100L, LocalDateTime.now(), false));
        telegramSummaryRepository.save(TelegramSummary.of(post, "gemini-3.5-flash-lite",
            "{\"summary\":\"요약\",\"key_points\":[],\"mentioned_tickers\":[],\"caveat\":\"고지\"}", 100, 50));
        telegramPostTickerRepository.save(TelegramPostTicker.of(post, "AAPL", "애플", "BULLISH", BigDecimal.valueOf(0.8)));
        return post;
    }

    @Test
    @DisplayName("[보존 기간(14일)보다 오래된 글과 그 자식 데이터(요약/태깅종목)를 전부 삭제하고, 최근 글은 남긴다]")
    void deletePostsOlderThanRetention_deletesOldPostAndChildRows_keepsRecentPost() {
        // given
        TelegramPost oldPost = seedPost("handle-old", 1L, LocalDateTime.now().minusDays(15));
        TelegramPost recentPost = seedPost("handle-recent", 1L, LocalDateTime.now().minusDays(3));

        // when
        int deletedCount = telegramPostRetentionService.deletePostsOlderThanRetention();
        // deleteAllByIdInBatch는 벌크 DELETE라 영속성 컨텍스트를 자동으로 비우지
        // 않는다(VideoRetentionServiceTest와 동일 이유) - flush+clear로 강제 초기화.
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(deletedCount).isEqualTo(1);
        assertThat(telegramPostRepository.findById(oldPost.getId())).isEmpty();
        assertThat(telegramSummaryRepository.findByTelegramPost(oldPost)).isEmpty();
        assertThat(telegramPostTickerRepository.findByTelegramPost(oldPost)).isEmpty();

        assertThat(telegramPostRepository.findById(recentPost.getId())).isPresent();
        assertThat(telegramSummaryRepository.findByTelegramPost(recentPost)).isPresent();
    }

    @Test
    @DisplayName("[보존 기간 초과 글이 없으면 아무것도 지우지 않고 0을 반환한다]")
    void deletePostsOlderThanRetention_nothingToDelete_returnsZero() {
        // given
        seedPost("handle-recent", 1L, LocalDateTime.now().minusDays(1));

        // when
        int deletedCount = telegramPostRetentionService.deletePostsOlderThanRetention();

        // then
        assertThat(deletedCount).isEqualTo(0);
        assertThat(telegramPostRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("[runExclusively는 락을 획득하면 삭제건수를 감싼 Optional을 반환한다]")
    void runExclusively_whenLockAcquired_returnsDeletedCount() {
        // given
        RedisLockService redisLockService = mock(RedisLockService.class);
        TelegramPostRetentionDeleteService telegramPostRetentionDeleteService = new TelegramPostRetentionDeleteService(
            telegramPostRepository, telegramSummaryRepository, telegramPostTickerRepository);
        TelegramPostRetentionService serviceWithLock = new TelegramPostRetentionService(
            redisLockService, telegramPostRepository, telegramPostRetentionDeleteService);
        seedPost("handle-old", 1L, LocalDateTime.now().minusDays(15));
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
        TelegramPostRetentionDeleteService telegramPostRetentionDeleteService = new TelegramPostRetentionDeleteService(
            telegramPostRepository, telegramSummaryRepository, telegramPostTickerRepository);
        TelegramPostRetentionService serviceWithLock = new TelegramPostRetentionService(
            redisLockService, telegramPostRepository, telegramPostRetentionDeleteService);
        seedPost("handle-old", 1L, LocalDateTime.now().minusDays(15));
        given(redisLockService.runExclusively(any(), any(), any())).willReturn(Optional.empty());

        // when
        Optional<Integer> result = serviceWithLock.runExclusively();

        // then
        assertThat(result).isEmpty();
        assertThat(telegramPostRepository.count()).isEqualTo(1);
    }
}
