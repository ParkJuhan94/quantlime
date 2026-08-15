package com.quantlime.telegramfeed.service;

import com.quantlime.telegramfeed.repository.TelegramDigestRepository;
import com.quantlime.telegramfeed.repository.TelegramDigestTickerRepository;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * VideoRetentionDeleteService와 동일한 이유로 분리된 별도 빈 - deleteBy...IdIn
 * 파생 삭제 쿼리는 SimpleJpaRepository의 클래스 레벨 @Transactional을 상속받지
 * 못해, self-invocation 경로(TelegramPostRetentionService.runExclusively ->
 * deleteOlderThanRetention)에서 호출하면 TransactionRequiredException을
 * 던진다(전역 CLAUDE.md "Spring Boot 프로젝트 코드 컨벤션 > 리포지토리" 참고).
 *
 * <p>다이제스트 재설계(2026-08-15) 이후 TelegramPost는 자식 테이블이 없다
 * (요약이 글이 아니라 채널×날짜 단위 TelegramDigest에 붙으므로) - 그래서
 * 글 삭제와 다이제스트 삭제를 별도 메서드로 분리했다. deleteAllByIdInBatch는
 * JpaRepository 내장 메서드라 원래도 자체적으로 트랜잭셔널하지만, 다이제스트
 * 쪽은 TelegramDigestTicker를 먼저 지워야 해 하나의 트랜잭션으로 묶었다.
 */
@Service
@RequiredArgsConstructor
public class TelegramPostRetentionDeleteService {

    private final TelegramPostRepository telegramPostRepository;
    private final TelegramDigestRepository telegramDigestRepository;
    private final TelegramDigestTickerRepository telegramDigestTickerRepository;

    @Transactional
    public void deletePostBatch(List<Long> telegramPostIds) {
        telegramPostRepository.deleteAllByIdInBatch(telegramPostIds);
    }

    @Transactional
    public void deleteDigestBatch(List<Long> telegramDigestIds) {
        telegramDigestTickerRepository.deleteByTelegramDigest_IdIn(telegramDigestIds);
        telegramDigestRepository.deleteAllByIdInBatch(telegramDigestIds);
    }
}
