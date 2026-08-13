package com.quantlime.telegramfeed.repository;

import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostTicker;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramPostTickerRepository extends JpaRepository<TelegramPostTicker, Long> {

    List<TelegramPostTicker> findByTelegramPost(TelegramPost telegramPost);

    List<TelegramPostTicker> findByTelegramPost_IdIn(List<Long> telegramPostIds);

    // 파생 삭제 쿼리 트랜잭션 미상속 주의사항은 TelegramSummaryRepository 참고.
    void deleteByTelegramPost_IdIn(List<Long> telegramPostIds);
}
