package com.quantlime.telegramfeed.service;

import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.telegramfeed.repository.TelegramPostTickerRepository;
import com.quantlime.telegramfeed.repository.TelegramSummaryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * VideoRetentionDeleteService와 동일한 이유로 분리된 별도 빈 - deleteBy...IdIn
 * 파생 삭제 쿼리는 SimpleJpaRepository의 클래스 레벨 @Transactional을 상속받지
 * 못해, self-invocation 경로(TelegramPostRetentionService.runExclusively ->
 * deletePostsOlderThanRetention)에서 호출하면 TransactionRequiredException을
 * 던진다(전역 CLAUDE.md "Spring Boot 프로젝트 코드 컨벤션 > 리포지토리" 참고).
 */
@Service
@RequiredArgsConstructor
public class TelegramPostRetentionDeleteService {

    private final TelegramPostRepository telegramPostRepository;
    private final TelegramSummaryRepository telegramSummaryRepository;
    private final TelegramPostTickerRepository telegramPostTickerRepository;

    @Transactional
    public void deleteBatch(List<Long> telegramPostIds) {
        telegramPostTickerRepository.deleteByTelegramPost_IdIn(telegramPostIds);
        telegramSummaryRepository.deleteByTelegramPost_IdIn(telegramPostIds);
        telegramPostRepository.deleteAllByIdInBatch(telegramPostIds);
    }
}
