package com.quantlime.batch.score;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.SkipListener;

/** skip된 종목 코드를 ERROR 로그로 남긴다 - 끝난 뒤 어느 종목이 빠졌는지 추적하기 위함. */
@Slf4j
public class ScoreRebuildSkipListener implements SkipListener<String, String> {

    private final String stepName;

    public ScoreRebuildSkipListener(String stepName) {
        this.stepName = stepName;
    }

    @Override
    public void onSkipInWrite(String stockCode, Throwable t) {
        log.error("스코어 재구성 종목 skip: step={}, stockCode={}, error={}", stepName, stockCode, t.getMessage(), t);
    }

    @Override
    public void onSkipInRead(Throwable t) {
        log.error("스코어 재구성 읽기 skip: step={}, error={}", stepName, t.getMessage(), t);
    }

    @Override
    public void onSkipInProcess(String stockCode, Throwable t) {
        log.error("스코어 재구성 처리 skip: step={}, stockCode={}, error={}", stepName, stockCode, t.getMessage(), t);
    }
}
