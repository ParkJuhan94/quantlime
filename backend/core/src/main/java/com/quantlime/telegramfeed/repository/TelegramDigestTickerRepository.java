package com.quantlime.telegramfeed.repository;

import com.quantlime.telegramfeed.domain.TelegramDigest;
import com.quantlime.telegramfeed.domain.TelegramDigestTicker;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramDigestTickerRepository extends JpaRepository<TelegramDigestTicker, Long> {

    List<TelegramDigestTicker> findByTelegramDigest(TelegramDigest telegramDigest);

    List<TelegramDigestTicker> findByTelegramDigest_IdIn(List<Long> telegramDigestIds);

    // 다이제스트 재생성(upsert) 시 이전 태깅 종목을 지우고 새로 채우는 용도 -
    // 같은 트랜잭션 내 호출(TelegramDigestPersistService)이라 파생 삭제
    // 쿼리의 트랜잭션 미상속 문제(전역 CLAUDE.md 참고)가 적용되지 않는다.
    void deleteByTelegramDigest(TelegramDigest telegramDigest);

    // 파생 삭제 쿼리 트랜잭션 미상속 주의사항은 TelegramSummaryRepository의
    // 기존 관례와 동일 - 호출부(TelegramDigestRetentionDeleteService)에
    // 반드시 @Transactional을 직접 붙일 것.
    void deleteByTelegramDigest_IdIn(List<Long> telegramDigestIds);
}
