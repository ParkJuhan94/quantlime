package com.quantlime.score.implement;

import com.quantlime.score.domain.Score;
import com.quantlime.score.repository.ScoreRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 스코어 조회를 감싸는 구현 레이어(Implementation) - 다른 도메인(market)의
 * 서비스가 score의 Repository를 직접 잡지 않고 이 컴포넌트를 통해서만 읽게 한다.
 */
@Component
@RequiredArgsConstructor
public class ScoreReader {

    private final ScoreRepository scoreRepository;

    /** 종목별 최신 스코어 산출일 스냅샷 - 배치 시작 시 한 번만 조회해 재사용한다. */
    public Map<String, LocalDate> findLatestScoreDateByStockCode() {
        return scoreRepository.findLatestScoreDateByStockCode();
    }

    public List<Score> findLatestScores(List<String> stockCodes) {
        return scoreRepository.findLatestScoresByStockCodesOrderByCompositeScoreDesc(stockCodes);
    }
}
