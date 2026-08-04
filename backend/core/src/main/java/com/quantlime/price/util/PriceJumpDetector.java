package com.quantlime.price.util;

import com.quantlime.price.dto.PriceJumpReport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 종목별 종가 시계열에서 정규장 가격제한폭으로는 설명되지 않는 급격한
 * 변동(액면분할/병합 등 수정주가 소급 재조정 의심)을 찾아낸다. DB 접근
 * 없는 순수 함수라 임계값 경계를 단위 테스트로 검증하기 쉽고, lag
 * 자기조인을 리포지토리에 native 쿼리로 밀어넣지 않아도 된다.
 */
public final class PriceJumpDetector {

    private PriceJumpDetector() {
    }

    /** points는 종목 하나의 시계열이며 tradeDate 오름차순이어야 한다. */
    public static List<PriceJumpReport> detect(String stockCode, List<PricePoint> points, double threshold) {
        List<PriceJumpReport> jumps = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            PricePoint previous = points.get(i - 1);
            PricePoint current = points.get(i);
            if (DailyPriceSettlementPolicy.isRestatement(previous.close(), current.close(), threshold)) {
                double ratio = current.close() / previous.close();
                jumps.add(new PriceJumpReport(
                    stockCode, current.tradeDate(), previous.tradeDate(),
                    previous.close(), current.close(), ratio));
            }
        }
        return jumps;
    }

    public record PricePoint(LocalDate tradeDate, double close) {
    }
}
