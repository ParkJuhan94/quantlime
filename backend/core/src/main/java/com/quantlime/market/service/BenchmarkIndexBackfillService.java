package com.quantlime.market.service;

import com.quantlime.market.domain.OverseasIndexCode;
import com.quantlime.market.implement.BenchmarkIndexCollector;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 백테스트 초과수익률 계산의 벤치마크 기준선(국내+해외 지수 일별 종가 이력)을 어떤
 * 지수에 대해 언제 갱신할지 정한다 - 외부 호출·페이지 루프·저장은
 * {@link BenchmarkIndexCollector}가 맡는다.
 *
 * <p>홈 화면 실시간 지수 표시({@code MarketIndexCache}/{@code DomesticIndexChartCache})는
 * 2026-07-30 세션에서 토스 공식 API(market-indicators)로 이관됐지만, 그건 이 클래스와
 * 별개 캐시 계층이다 - 이 백테스트 벤치마크 백필은 국내/해외 모두 계속 네이버 소스를
 * 쓴다(토스 시장 지표 심볼 카탈로그는 국내 지수·국채만 지원해 해외는 애초에 대상이
 * 아니었고, 국내도 아직 이관하지 않았다).
 */
@Service
@RequiredArgsConstructor
public class BenchmarkIndexBackfillService {

    private static final List<String> DOMESTIC_INDEX_CODES = List.of("KOSPI", "KOSDAQ");
    // 해외종목(나스닥/뉴욕) 백테스트·스코어 벤치마크. 저장 키(indexCode)는
    // 사람이 읽기 쉬운 이름("NASDAQ"/"SP500")으로 두고, 네이버 조회에만
    // 로이터 코드(OverseasIndexCode)를 쓴다 - 홈 화면 지수카드
    // (OverseasIndexChartCache)와 같은 데이터 소스지만 그쪽은 60초 TTL
    // 캐시일 뿐 영속 저장을 안 해서 벤치마크 용도로는 별도 백필이 필요했다.
    private static final Map<String, OverseasIndexCode> OVERSEAS_INDEX_CODES = Map.of(
        "NASDAQ", OverseasIndexCode.NASDAQ,
        "SP500", OverseasIndexCode.SP500
    );
    private static final int BACKFILL_TARGET_DAYS = 400;

    private final BenchmarkIndexCollector benchmarkIndexCollector;

    public void backfillAllIfNeeded() {
        for (String indexCode : DOMESTIC_INDEX_CODES) {
            backfillIfNeeded(indexCode, BACKFILL_TARGET_DAYS);
        }
        OVERSEAS_INDEX_CODES.forEach((indexCode, overseasIndexCode) ->
            backfillOverseasIfNeeded(indexCode, overseasIndexCode, BACKFILL_TARGET_DAYS));
    }

    public void backfillIfNeeded(String indexCode, int targetDays) {
        benchmarkIndexCollector.backfillDomestic(indexCode, targetDays);
    }

    public void backfillOverseasIfNeeded(String indexCode, OverseasIndexCode overseasIndexCode, int targetDays) {
        benchmarkIndexCollector.backfillWorld(indexCode, overseasIndexCode, targetDays);
    }

    /**
     * 국내+해외 지수의 최신 종가만 갱신한다(매일 트리거에 물린 경로) - "이미 목표치만큼
     * 쌓였으면 스킵"하는 {@link #backfillAllIfNeeded}와 달리 항상 최신 페이지를 조회한다
     * (스킵 로직 때문에 벤치마크가 며칠씩 갭인 채로 멈췄던 2026-07-30·07-31 버그 참고).
     */
    public void refreshRecentIfNeeded() {
        for (String indexCode : DOMESTIC_INDEX_CODES) {
            benchmarkIndexCollector.refreshRecentDomestic(indexCode);
        }
        OVERSEAS_INDEX_CODES.forEach(benchmarkIndexCollector::refreshRecentWorld);
    }
}
