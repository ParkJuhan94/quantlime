package com.quantlime.score.domain;

import com.quantlime.price.util.DailyPriceSettlementPolicy;
import java.time.LocalDate;

/**
 * 종목 하나의 날짜별 스코어 시계열을 저장할 때 "이미 있어도 덮어쓸 날짜"를 가르는 규칙.
 * 가장 최근 날짜(보통 오늘/최신 거래일)는 재계산마다 항상 덮어쓰고, 가격 재확정 윈도우
 * ({@link DailyPriceSettlementPolicy#RESETTLEMENT_WINDOW_DAYS}) 안의 날짜도 마찬가지다 -
 * 가격이 그 윈도우 안에서 사후 보정될 수 있는데 스코어만 "과거는 있으면 스킵"이면 고쳐진 가격이
 * 스코어에 영영 반영되지 않는다. 윈도우보다 오래된 날짜는 이미 있으면 건드리지 않는다.
 */
public final class ScoreSeriesPolicy {

    private ScoreSeriesPolicy() {
    }

    public static boolean shouldOverwrite(LocalDate scoreDate, LocalDate latestDate, LocalDate today) {
        return scoreDate.equals(latestDate) || DailyPriceSettlementPolicy.isWithinWindow(scoreDate, today);
    }
}
