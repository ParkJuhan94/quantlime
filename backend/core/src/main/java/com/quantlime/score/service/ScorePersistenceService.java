package com.quantlime.score.service;

import com.quantlime.infra.python.dto.ScoreSeriesBatchApiResponse.DailyScoreSeriesApiResponse;
import com.quantlime.infra.python.dto.ScoreSeriesBatchApiResponse.StockScoreSeriesApiResponse;
import com.quantlime.score.domain.Score;
import com.quantlime.score.dto.mapper.ScoreMapper;
import com.quantlime.score.repository.ScoreRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 퀀트 엔진 응답을 {@link Score}로 영속화하는 책임만 따로 분리했다.
 * {@link ScoreService}가 외부 HTTP 호출까지 감싸는 트랜잭션을 만들지 않도록
 * (커넥션을 불필요하게 오래 붙잡지 않도록) 저장 전용의 별도 빈으로 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScorePersistenceService {

    private final ScoreRepository scoreRepository;

    /**
     * 종목 하나의 저장 실패(등급 코드 불일치, 동시 재계산과의 유니크 제약
     * 경합 등)가 같은 배치의 나머지 종목 저장까지 막지 않도록 항목별로
     * 예외를 격리한다.
     */
    @Transactional
    public void saveAll(List<StockScoreSeriesApiResponse> results) {
        for (StockScoreSeriesApiResponse result : results) {
            try {
                saveSeries(result.stockCode(), result.dailyScores());
            } catch (Exception e) {
                log.error("스코어 저장 실패(해당 종목만 스킵하고 계속): stockCode={}, error={}",
                    result.stockCode(), e.getMessage(), e);
            }
        }
    }

    /**
     * 종목 하나의 날짜별 스코어 시계열을 upsert한다 - 가장 최근 날짜(보통
     * 오늘/최신 거래일)는 재계산마다 항상 값을 덮어쓰고, 그보다 과거 날짜는
     * 이미 저장돼 있으면 스킵한다(가격 백필의 "오늘만 덮어쓰기, 과거는
     * 존재하면 스킵" 규칙과 동일한 원칙 - DailyPriceService 참고). 지표
     * 워밍업 구간(insufficient_data=true)은 저장할 값이 없어 건너뛴다.
     */
    private void saveSeries(String stockCode, List<DailyScoreSeriesApiResponse> dailyScores) {
        if (dailyScores.isEmpty()) {
            return;
        }
        LocalDate latestDate = dailyScores.stream()
            .map(row -> LocalDate.parse(row.date()))
            .max(Comparator.naturalOrder())
            .orElseThrow();

        for (DailyScoreSeriesApiResponse row : dailyScores) {
            if (row.insufficientData()) {
                continue;
            }
            LocalDate scoreDate = LocalDate.parse(row.date());
            if (scoreDate.equals(latestDate)) {
                upsert(stockCode, scoreDate, row);
            } else if (!scoreRepository.existsByStockCodeAndScoreDate(stockCode, scoreDate)) {
                create(stockCode, scoreDate, row);
            }
        }
    }

    private void upsert(String stockCode, LocalDate scoreDate, DailyScoreSeriesApiResponse row) {
        scoreRepository.findByStockCodeAndScoreDate(stockCode, scoreDate)
            .ifPresentOrElse(
                existing -> ScoreMapper.updateScoreFrom(existing, row),
                () -> create(stockCode, scoreDate, row));
    }

    private void create(String stockCode, LocalDate scoreDate, DailyScoreSeriesApiResponse row) {
        try {
            scoreRepository.save(ScoreMapper.toScore(stockCode, scoreDate, row));
        } catch (DataIntegrityViolationException e) {
            log.debug("스코어 이력 중복 저장 스킵: stockCode={}, date={}", stockCode, scoreDate);
        }
    }
}
