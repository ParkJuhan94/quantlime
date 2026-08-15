package com.quantlime.telegramfeed.repository;

import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostStatus;
import com.quantlime.videofeed.domain.Channel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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

    // max_per_run 하루 쿼터 재계산용 - 같은 날짜 후보가 여러 수집 사이클에
    // 걸쳐 나뉘어 들어올 수 있어 이번 배치의 후보 개수만 보지 않고 그
    // 날짜에 이미 SELECTED된 개수를 DB에서 다시 센다(VideoRepository
    // .countByChannelAndStatusAndPublishedAtBetween과 동일 이유).
    int countByChannelAndStatusAndPublishedAtBetween(
        Channel channel, TelegramPostStatus status, LocalDateTime start, LocalDateTime end);

    // SummaryCollectionFacade(유튜브)의 findSummarizeCandidates와 동일 이유로
    // join fetch p.channel을 강제한다 - 트랜잭션 밖에서
    // post.getChannel().getName()을 읽으면 LazyInitializationException이 난다.
    // 텔레그램은 자막 단계가 없어(본문이 이미 텍스트) "and exists Transcript"
    // 조건이 없다 - status=SELECTED 자체가 유튜브의 TRANSCRIBED에 대응한다.
    @Query("select p from TelegramPost p join fetch p.channel "
        + "where p.status in :statuses and p.retryCount < :maxRetryCount "
        + "and not exists (select 1 from TelegramSummary s where s.telegramPost = p) "
        + "order by p.publishedAt asc")
    Slice<TelegramPost> findSummarizeCandidates(
        @Param("statuses") List<TelegramPostStatus> statuses, @Param("maxRetryCount") int maxRetryCount,
        Pageable pageable);

    // P7-5(공개 조회) - 요약까지 끝난 글만 최신순으로 공개 노출한다.
    // VideoRepository.findSummarizedVideos와 동일한 ":param is null or ..." 패턴 -
    // tickerCode/channelId/publishedFrom/publishedTo는 전부 선택 필터.
    @Query("select p from TelegramPost p join fetch p.channel "
        + "where p.status = com.quantlime.telegramfeed.domain.TelegramPostStatus.SUMMARIZED "
        + "and (:tickerCode is null or exists "
        + "  (select 1 from TelegramPostTicker t where t.telegramPost = p and t.tickerCode = :tickerCode)) "
        + "and (:channelId is null or p.channel.id = :channelId) "
        + "and (:publishedFrom is null or p.publishedAt >= :publishedFrom) "
        + "and (:publishedTo is null or p.publishedAt < :publishedTo) "
        + "order by p.publishedAt desc")
    Slice<TelegramPost> findSummarizedPosts(
        @Param("tickerCode") String tickerCode,
        @Param("channelId") Long channelId,
        @Param("publishedFrom") LocalDateTime publishedFrom,
        @Param("publishedTo") LocalDateTime publishedTo,
        Pageable pageable);

    @Query("select p from TelegramPost p join fetch p.channel "
        + "where p.id = :telegramPostId and p.status = com.quantlime.telegramfeed.domain.TelegramPostStatus.SUMMARIZED")
    Optional<TelegramPost> findSummarizedPostById(@Param("telegramPostId") Long telegramPostId);
}
