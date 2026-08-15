package com.quantlime.telegramfeed.repository;

import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostStatus;
import com.quantlime.videofeed.domain.Channel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TelegramPostRepository extends JpaRepository<TelegramPost, Long> {

    boolean existsByExternalPostId(String externalPostId);

    // 증분 수집 커서(?after=) 계산용 - 채널에 이미 저장된 글 중 가장 큰
    // messageId. Channel에 별도 커서 컬럼을 두지 않고 매번 여기서 유도한다
    // (desync 위험 없음 - docs/ROADMAP.md "Phase 8 P7" 참고).
    @Query("select max(p.messageId) from TelegramPost p where p.channel = :channel")
    Optional<Long> findMaxMessageIdByChannel(@Param("channel") Channel channel);

    List<TelegramPost> findByChannelAndStatus(Channel channel, TelegramPostStatus status);

    // TelegramDigestGenerationService의 다이제스트 재료 조회용 - 채널의
    // 오늘(또는 특정일) SELECTED 글 전체가 다이제스트로 합쳐 요약된다.
    // join fetch는 없다(호출부가 content만 읽고 channel은 이미 알고 있어
    // LazyInitializationException 위험이 없음).
    List<TelegramPost> findByChannelAndStatusAndPublishedAtBetween(
        Channel channel, TelegramPostStatus status, LocalDateTime start, LocalDateTime end);

    // 보존 기간(TelegramPostRetentionService.RETENTION_DAYS) 정리용 - 상태 무관하게
    // 발행일 기준으로만 대상을 잡는다(VideoRepository.findIdsByPublishedAtBefore와
    // 동일 이유 - FILTERED_OUT처럼 피드에 노출된 적 없는 글도 함께 정리 대상).
    @Query("select p.id from TelegramPost p where p.publishedAt < :cutoff")
    List<Long> findIdsByPublishedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
