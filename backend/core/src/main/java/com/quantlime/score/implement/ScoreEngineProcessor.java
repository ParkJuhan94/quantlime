package com.quantlime.score.implement;

import com.quantlime.infra.python.PythonEngineClient;
import com.quantlime.infra.python.dto.CrossSectionNormalizeApiRequest;
import com.quantlime.infra.python.dto.CrossSectionNormalizeApiResponse;
import com.quantlime.infra.python.dto.CrossSectionNormalizeApiResponse.NormalizedItemApiResponse;
import com.quantlime.infra.python.dto.ScoreBatchApiRequest;
import com.quantlime.infra.python.dto.ScoreSeriesBatchApiResponse;
import com.quantlime.infra.python.dto.ScoreSeriesBatchApiResponse.StockScoreSeriesApiResponse;
import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.OverseasDailyPrice;
import com.quantlime.score.domain.PeerGroup;
import com.quantlime.score.domain.Score;
import com.quantlime.score.dto.mapper.ScoreRequestMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 퀀트 엔진(Python) 호출 흐름을 감싸는 구현 레이어 - 도메인 데이터를 엔진 요청으로 바꾸고,
 * 호출하고, 응답을 검증(요청했는데 응답에 빠진 종목 경고·메트릭)한 결과만 돌려준다. 서비스는
 * 엔진 클라이언트·요청/응답 변환을 몰라도 되고 "어떤 종목을 언제 계산하고 어디에 저장할지"만
 * 결정한다. 이 컴포넌트는 트랜잭션을 만들지 않는다(HTTP 대기 중 DB 커넥션을 붙잡지 않기 위해
 * 호출 흐름과 저장을 분리해둔 기존 구조 유지).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreEngineProcessor {

    private static final String METRIC_MISSING_FROM_RESPONSE = "score.batch.missing-from-response";

    private final PythonEngineClient pythonEngineClient;
    private final MeterRegistry meterRegistry;

    /** 국내 종목들의 일봉 시계열로 날짜별 스코어를 계산한다. */
    public List<StockScoreSeriesApiResponse> calculateDomestic(
        Map<String, List<DomesticDailyPrice>> pricesByStockCode) {
        ScoreBatchApiRequest request = ScoreRequestMapper.toScoreBatchApiRequest(pricesByStockCode);
        return calculate(request, pricesByStockCode.keySet());
    }

    /** 해외 종목들의 일봉 시계열로 날짜별 스코어를 계산한다. */
    public List<StockScoreSeriesApiResponse> calculateOverseas(
        Map<String, List<OverseasDailyPrice>> pricesByStockCode) {
        ScoreBatchApiRequest request = ScoreRequestMapper.toOverseasScoreBatchApiRequest(pricesByStockCode);
        return calculate(request, pricesByStockCode.keySet());
    }

    private List<StockScoreSeriesApiResponse> calculate(ScoreBatchApiRequest request, Set<String> requested) {
        ScoreSeriesBatchApiResponse response = pythonEngineClient.calculateScoreSeries(request);
        warnIfMissingFromResponse(requested, response.scores());
        return response.scores();
    }

    /**
     * 최신 스코어들의 횡단면 백분위를 엔진에서 받는다. 표본이 엔진의 최소 기준(MIN_STOCKS_PER_DATE)
     * 미만이면 그 날짜의 순위 자체가 불안정하므로 비어 있는 결과를 돌려준다(반영하지 않음).
     */
    public Optional<List<NormalizedItemApiResponse>> normalize(PeerGroup peerGroup, List<Score> latestScores) {
        CrossSectionNormalizeApiRequest request = ScoreRequestMapper.toNormalizeRequest(
            LocalDate.now(), peerGroup, latestScores);
        CrossSectionNormalizeApiResponse response = pythonEngineClient.normalizeCrossSection(request);
        if (!response.minSampleMet()) {
            return Optional.empty();
        }
        return Optional.of(response.items());
    }

    private void warnIfMissingFromResponse(Set<String> requested, List<StockScoreSeriesApiResponse> results) {
        Set<String> responded = results.stream()
            .map(StockScoreSeriesApiResponse::stockCode)
            .collect(Collectors.toSet());
        List<String> missing = requested.stream()
            .filter(code -> !responded.contains(code))
            .toList();
        if (!missing.isEmpty()) {
            log.warn("퀀트 엔진 응답에 누락된 종목 존재(저장 스킵): stockCodes={}", missing);
            meterRegistry.counter(METRIC_MISSING_FROM_RESPONSE).increment(missing.size());
        }
    }
}
