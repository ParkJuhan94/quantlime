package com.quantlime.batch.common;

import com.quantlime.batch.backtest.BacktestJobConfig;
import com.quantlime.batch.score.ScoreRebuildJobConfig;
import java.util.List;
import java.util.Map;

/**
 * 동시에 실행하면 안 되는 Job 묶음. 같은 그룹 안에서는 이름이 다른 Job이어도 하나가 실행 중이면 나머지는 시작할 수 없다 -
 * 예전 백테스트 스케줄러의 Redis 락({@code lock:backtest-weekly})이 하던 역할을 JobExplorer 검사로 대신한다.
 * 그룹에 없는 Job은 자기 자신하고만 배제된다.
 */
final class BatchJobGroups {

    private static final List<String> BACKTEST = List.of(
        BacktestJobConfig.WEEKLY_JOB, BacktestJobConfig.DATASET_JOB,
        BacktestJobConfig.UNIVERSE_JOB, BacktestJobConfig.CROSS_SECTIONAL_JOB);

    private static final Map<String, List<String>> GROUP_BY_JOB = Map.of(
        BacktestJobConfig.WEEKLY_JOB, BACKTEST,
        BacktestJobConfig.DATASET_JOB, BACKTEST,
        BacktestJobConfig.UNIVERSE_JOB, BACKTEST,
        BacktestJobConfig.CROSS_SECTIONAL_JOB, BACKTEST,
        ScoreRebuildJobConfig.JOB_NAME, List.of(ScoreRebuildJobConfig.JOB_NAME));

    private BatchJobGroups() {
    }

    /** 이 Job을 시작하기 전에 실행 중이 아니어야 하는 Job 이름들(자기 자신 포함). */
    static List<String> exclusiveWith(String jobName) {
        return GROUP_BY_JOB.getOrDefault(jobName, List.of(jobName));
    }
}
