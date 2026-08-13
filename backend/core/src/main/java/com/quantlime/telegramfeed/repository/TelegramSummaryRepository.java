package com.quantlime.telegramfeed.repository;

import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramSummaryRepository extends JpaRepository<TelegramSummary, Long> {

    Optional<TelegramSummary> findByTelegramPost(TelegramPost telegramPost);

    List<TelegramSummary> findByTelegramPost_IdIn(List<Long> telegramPostIds);

    // 파생 삭제 쿼리는 SimpleJpaRepository의 클래스 레벨 @Transactional을
    // 상속받지 못한다 - 호출부(TelegramPostRetentionDeleteService, P7-6)에
    // 반드시 @Transactional을 직접 붙일 것(전역 CLAUDE.md "Spring Boot
    // 프로젝트 코드 컨벤션 > 리포지토리" 참고, 이 프로젝트에서 실제로
    // 겪은 버그 패턴).
    void deleteByTelegramPost_IdIn(List<Long> telegramPostIds);
}
