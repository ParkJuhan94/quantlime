package com.quantlab.score.repository;

import com.quantlab.score.domain.Score;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreRepository extends JpaRepository<Score, Long>, ScoreQueryRepository {

    Optional<Score> findByStockCodeAndScoreDate(String stockCode, LocalDate scoreDate);

    Optional<Score> findTopByStockCodeOrderByScoreDateDesc(String stockCode);
}
