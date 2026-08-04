package com.quantlime.score.repository;

import com.quantlime.score.domain.Score;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScoreRepository extends JpaRepository<Score, Long>, ScoreQueryRepository {

    Optional<Score> findByStockCodeAndScoreDate(String stockCode, LocalDate scoreDate);

    boolean existsByStockCodeAndScoreDate(String stockCode, LocalDate scoreDate);

    Optional<Score> findTopByStockCodeOrderByScoreDateDesc(String stockCode);

    // 가격 소급 복구 후 오염된 과거 스코어를 일괄 정리하는 복구 전용 쿼리
    // (ScorePersistenceService.deleteFrom). score_version이 없어 "지우고
    // 다시 만들기"가 유일하고 가장 정확한 복구 방법이다.
    @Modifying
    @Query("delete from Score s where s.scoreDate >= :from")
    int deleteByScoreDateGreaterThanEqual(@Param("from") LocalDate from);
}
