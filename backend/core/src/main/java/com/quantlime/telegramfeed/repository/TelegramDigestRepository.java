package com.quantlime.telegramfeed.repository;

import com.quantlime.telegramfeed.domain.TelegramDigest;
import com.quantlime.videofeed.domain.Channel;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TelegramDigestRepository extends JpaRepository<TelegramDigest, Long> {

    // 다이제스트 재생성(수집 사이클마다 누적 재요약) 시 upsert 대상을
    // 찾는 용도 - uk_telegram_digest(channel_id, digest_date) 유니크
    // 제약과 대응.
    Optional<TelegramDigest> findByChannelAndDigestDate(Channel channel, LocalDate digestDate);

    // P7-5(공개 조회) - VideoRepository.findSummarizedVideos와 동일한
    // ":param is null or ..." 패턴. tickerCode/channelId/date는 전부 선택
    // 필터. 다이제스트는 "요약됨" 상태 개념이 없다 - 존재 자체가 요약
    // 완료를 뜻한다.
    @Query("select d from TelegramDigest d join fetch d.channel "
        + "where (:tickerCode is null or exists "
        + "  (select 1 from TelegramDigestTicker t where t.telegramDigest = d and t.tickerCode = :tickerCode)) "
        + "and (:channelId is null or d.channel.id = :channelId) "
        + "and (:date is null or d.digestDate = :date) "
        + "order by d.digestDate desc")
    Slice<TelegramDigest> findDigests(
        @Param("tickerCode") String tickerCode,
        @Param("channelId") Long channelId,
        @Param("date") LocalDate date,
        Pageable pageable);

    @Query("select d from TelegramDigest d join fetch d.channel where d.id = :telegramDigestId")
    Optional<TelegramDigest> findByIdWithChannel(@Param("telegramDigestId") Long telegramDigestId);

    // 보존 기간 정리용 - TelegramPostRetentionService.RETENTION_DAYS와
    // 동일 값을 기준으로 digest_date가 오래된 다이제스트를 함께 정리한다.
    @Query("select d.id from TelegramDigest d where d.digestDate < :cutoff")
    List<Long> findIdsByDigestDateBefore(@Param("cutoff") LocalDate cutoff);
}
