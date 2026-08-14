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

    // max_per_run 하루 쿼터 재계산용 - 같은 날짜 후보가 여러 수집 사이클에
    // 걸쳐 나뉘어 들어올 수 있어 이번 배치의 후보 개수만 보지 않고 그
    // 날짜에 이미 SELECTED된 개수를 DB에서 다시 센다(VideoRepository
    // .countByChannelAndStatusAndPublishedAtBetween과 동일 이유).
    int countByChannelAndStatusAndPublishedAtBetween(
        Channel channel, TelegramPostStatus status, LocalDateTime start, LocalDateTime end);
}
