package com.quantlab.score.service;

import com.quantlab.infra.python.dto.ScoreBatchApiResponse.StockScoreApiResponse;
import com.quantlab.score.domain.Score;
import com.quantlab.score.dto.mapper.ScoreMapper;
import com.quantlab.score.repository.ScoreRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public void saveAll(List<StockScoreApiResponse> results) {
        for (StockScoreApiResponse result : results) {
            try {
                saveScore(result);
            } catch (Exception e) {
                log.error("스코어 저장 실패(해당 종목만 스킵하고 계속): stockCode={}, error={}",
                    result.stockCode(), e.getMessage(), e);
            }
        }
    }

    private void saveScore(StockScoreApiResponse apiResponse) {
        LocalDate today = LocalDate.now();
        scoreRepository.findByStockCodeAndScoreDate(apiResponse.stockCode(), today)
            .ifPresentOrElse(
                existing -> ScoreMapper.updateScoreFrom(existing, apiResponse),
                () -> scoreRepository.save(
                    ScoreMapper.toScore(apiResponse.stockCode(), today, apiResponse)));
    }
}
