package com.quantlime.batch.backtest;

import com.quantlime.batch.common.BatchJobLaunchService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 백테스트 Job 4개를 시작하는 진입점. 주간 스케줄러와 관리자 API가 같은 경로를 쓴다. 반환값은 executionId이고, 진행 상태는
 * {@link BatchJobLaunchService#getExecution}로 본다. 같은 그룹의 백테스트 Job이 실행 중이면
 * {@link com.quantlime.batch.common.BatchLaunchRejectedException}이 난다.
 */
@Component
@RequiredArgsConstructor
public class BacktestBatchLauncher {

    private final BatchJobLaunchService launchService;

    public long launchWeekly() {
        return launchService.launch(BacktestJobConfig.WEEKLY_JOB, launchService.newParameters().toJobParameters());
    }

    public long launchDataset() {
        return launchService.launch(BacktestJobConfig.DATASET_JOB, launchService.newParameters().toJobParameters());
    }

    public long launchUniverse(boolean force) {
        JobParametersBuilder parameters = launchService.newParameters()
            .addString(BacktestJobConfig.PARAM_FORCE, Boolean.toString(force));
        return launchService.launch(BacktestJobConfig.UNIVERSE_JOB, parameters.toJobParameters());
    }

    /** market이 null이면 4개 시장 전부, 아니면 그 시장만 실행한다. */
    public long launchCrossSectional(@Nullable String market, String scoreVersion, boolean nullTest, int nullRepeats) {
        JobParametersBuilder parameters = launchService.newParameters()
            .addString(BacktestJobConfig.PARAM_SCORE_VERSION, scoreVersion)
            .addString(BacktestJobConfig.PARAM_NULL_TEST, Boolean.toString(nullTest))
            .addString(BacktestJobConfig.PARAM_NULL_REPEATS, Integer.toString(nullRepeats));
        if (market != null && !market.isBlank()) {
            parameters.addString(BacktestJobConfig.PARAM_MARKET, market);
        }
        return launchService.launch(BacktestJobConfig.CROSS_SECTIONAL_JOB, parameters.toJobParameters());
    }
}
