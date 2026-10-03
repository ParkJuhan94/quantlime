package com.quantlime.market.implement;

import com.quantlime.market.domain.BenchmarkIndex;
import com.quantlime.market.repository.BenchmarkIndexRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 벤치마크 지수 이력 조회를 감싸는 구현 레이어(Implementation). */
@Component
@RequiredArgsConstructor
public class BenchmarkIndexReader {

    private final BenchmarkIndexRepository benchmarkIndexRepository;

    public long count(String indexCode) {
        return benchmarkIndexRepository.countByIndexCode(indexCode);
    }

    public boolean exists(String indexCode, LocalDate tradeDate) {
        return benchmarkIndexRepository.existsByIndexCodeAndTradeDate(indexCode, tradeDate);
    }

    /** {@code date} 이전의 가장 최근 종가 행(전일 종가 계산용). */
    public Optional<BenchmarkIndex> findLatestBefore(String indexCode, LocalDate date) {
        return benchmarkIndexRepository
            .findTopByIndexCodeAndTradeDateLessThanOrderByTradeDateDesc(indexCode, date);
    }

    public List<BenchmarkIndex> findBetween(String indexCode, LocalDate from, LocalDate to) {
        return benchmarkIndexRepository
            .findByIndexCodeAndTradeDateBetweenOrderByTradeDateAsc(indexCode, from, to);
    }
}
