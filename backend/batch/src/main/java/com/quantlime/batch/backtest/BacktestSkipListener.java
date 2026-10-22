package com.quantlime.batch.backtest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.SkipListener;

/** skip된 종목 코드를 ERROR 로그로 남긴다 - 이력 부족 같은 종목별 실패를 끝난 뒤 추적하기 위함. */
@Slf4j
public class BacktestSkipListener implements SkipListener<String, String> {

    private final String stepName;

    public BacktestSkipListener(String stepName) {
        this.stepName = stepName;
    }

    @Override
    public void onSkipInWrite(String stockCode, Throwable t) {
        log.error("유니버스 백테스트 종목 skip: step={}, stockCode={}, error={}", stepName, stockCode, t.getMessage(), t);
    }

    @Override
    public void onSkipInRead(Throwable t) {
        log.error("유니버스 백테스트 읽기 skip: step={}, error={}", stepName, t.getMessage(), t);
    }

    @Override
    public void onSkipInProcess(String stockCode, Throwable t) {
        log.error("유니버스 백테스트 처리 skip: step={}, stockCode={}, error={}", stepName, stockCode, t.getMessage(), t);
    }
}
