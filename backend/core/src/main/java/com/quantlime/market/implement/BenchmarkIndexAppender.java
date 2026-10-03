package com.quantlime.market.implement;

import com.quantlime.market.domain.BenchmarkIndex;
import com.quantlime.market.repository.BenchmarkIndexRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 벤치마크 지수 이력 저장을 감싸는 구현 레이어(Implementation) - 엔티티 생성과
 * 저장을 한 곳에 모은다. 중복 저장 시 {@code DataIntegrityViolationException}은
 * 그대로 전파되며, 건별 커밋·스킵 정책은 호출부(백필 서비스)가 정한다.
 */
@Component
@RequiredArgsConstructor
public class BenchmarkIndexAppender {

    private final BenchmarkIndexRepository benchmarkIndexRepository;

    public void append(
        String indexCode, LocalDate tradeDate,
        double open, double high, double low, double close) {
        benchmarkIndexRepository.save(BenchmarkIndex.of(indexCode, tradeDate, open, high, low, close));
    }
}
