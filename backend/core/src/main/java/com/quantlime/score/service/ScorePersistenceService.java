package com.quantlime.score.service;

import com.quantlime.infra.python.dto.ScoreSeriesBatchApiResponse.DailyScoreSeriesApiResponse;
import com.quantlime.infra.python.dto.ScoreSeriesBatchApiResponse.StockScoreSeriesApiResponse;
import com.quantlime.price.util.DailyPriceSettlementPolicy;
import com.quantlime.score.domain.Score;
import com.quantlime.score.dto.mapper.ScoreMapper;
import com.quantlime.score.repository.ScoreRepository;
import jakarta.persistence.EntityManager;
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
    private final EntityManager entityManager;

    /**
     * 종목 하나의 저장 실패(등급 코드 불일치, 동시 재계산과의 유니크 제약
     * 경합 등)가 같은 배치의 나머지 종목 저장까지 막지 않도록 항목별로
     * 예외를 격리한다.
     *
     * <p>종목 하나를 처리할 때마다 영속성 컨텍스트를 flush+clear한다 -
     * 안 그러면 한 트랜잭션 안에서 관리 엔티티가 계속 누적돼, Hibernate가
     * 쿼리 실행 전 auto-flush 시점마다 세션 내 전체 엔티티를 dirty-check하는
     * 비용이 종목 수에 비례해 커진다. 평소 라이브 경로(종목당 최신 며칠만
     * upsert)는 세션이 작아 드러나지 않지만, {@link ScoreService#rebuildScoresFrom}
     * 처럼 종목당 수백 일치 스코어를 통째로 새로 만드는 대량 복구 시나리오에서
     * 실제로 청크 하나가 사실상 멈추는 것을 확인했다(2026-08-04, jstack으로
     * DefaultFlushEntityEventListener.hasDirtyCollections에서 멈춰 있는 것 확인).
     */
    @Transactional
    public void saveAll(List<StockScoreSeriesApiResponse> results) {
        for (StockScoreSeriesApiResponse result : results) {
            try {
                saveSeries(result.stockCode(), result.dailyScores());
            } catch (Exception e) {
                log.error("스코어 저장 실패(해당 종목만 스킵하고 계속): stockCode={}, error={}",
                    result.stockCode(), e.getMessage(), e);
            } finally {
                entityManager.flush();
                entityManager.clear();
            }
        }
    }

    /**
     * 종목 하나의 날짜별 스코어 시계열을 upsert한다 - 가장 최근 날짜(보통
     * 오늘/최신 거래일)는 재계산마다 항상 값을 덮어쓰고, 재확정 윈도우
     * ({@link DailyPriceSettlementPolicy#RESETTLEMENT_WINDOW_DAYS}) 안의
     * 날짜도 마찬가지로 항상 덮어쓴다 - 가격이 그 윈도우 안에서 사후
     * 보정될 수 있는데(같은 정책, DomesticDailyPriceService 참고) 스코어만
     * "과거는 존재하면 스킵"이면 고쳐진 가격이 스코어에 영영 반영되지
     * 않는다. 윈도우보다 오래된 날짜는 이미 저장돼 있으면 스킵한다. 지표
     * 워밍업 구간(insufficient_data=true)은 저장할 값이 없어 건너뛴다.
     */
    private void saveSeries(String stockCode, List<DailyScoreSeriesApiResponse> dailyScores) {
        if (dailyScores.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate latestDate = dailyScores.stream()
            .map(row -> LocalDate.parse(row.date()))
            .max(Comparator.naturalOrder())
            .orElseThrow();

        for (DailyScoreSeriesApiResponse row : dailyScores) {
            if (row.insufficientData()) {
                continue;
            }
            LocalDate scoreDate = LocalDate.parse(row.date());
            if (scoreDate.equals(latestDate) || DailyPriceSettlementPolicy.isWithinWindow(scoreDate, today)) {
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

    /**
     * 가격 소급 복구 후 오염된 과거 스코어를 일괄 정리하는 복구 전용 메서드
     * ({@link ScoreService#rebuildScoresFrom}가 호출). {@code Score}에는
     * score_version이 없고 unique가 {stock_code, score_date}뿐이라 "지우고
     * 다시 만들기"가 가장 정확하고 싸다.
     */
    @Transactional
    public void deleteFrom(LocalDate from) {
        int deleted = scoreRepository.deleteByScoreDateGreaterThanEqual(from);
        log.info("스코어 일괄 삭제 완료: from={}, 삭제건수={}", from, deleted);
    }
}
