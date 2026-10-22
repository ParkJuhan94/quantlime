package com.quantlime.batch.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.quantlime.backtest.dto.response.UniverseSelection;
import com.quantlime.batch.BatchIntegrationTestSupport;
import com.quantlime.batch.backtest.BacktestBatchLauncher;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 최근 실행 목록: 모든 Job을 합쳐 최신순으로 자르고, 목록에는 Step을 싣지 않는다. */
class BatchJobLaunchServiceTest extends BatchIntegrationTestSupport {

    @Autowired
    private BacktestBatchLauncher launcher;

    @Test
    @DisplayName("[서로 다른 Job의 실행이 만든 시각 내림차순으로 합쳐지고, limit만큼만 돌아오며, 목록에는 Step이 없다]")
    void recentExecutions_mergesJobsNewestFirst_withoutSteps() {
        given(datasetPreparationService.prepareDataset()).willReturn(new UniverseSelection(List.of(), List.of()));
        given(stockMasterService.getAllListedStocks()).willReturn(List.of());

        long first = launchService.launchScoreRebuild(LocalDate.of(2026, 10, 1));
        runToEnd(first);
        long second = launcher.launchDataset();
        runToEnd(second);
        long third = launchService.launchScoreRebuild(LocalDate.of(2026, 10, 2));
        runToEnd(third);

        List<BatchExecutionView> recent = launchService.recentExecutions(50);
        List<Long> ids = recent.stream().map(BatchExecutionView::executionId).toList();

        // 같은 DB를 쓰는 다른 테스트의 실행이 섞여 있을 수 있어, 이 테스트가 만든 세 건의 상대 순서만 본다
        assertThat(ids).contains(first, second, third);
        assertThat(ids.indexOf(third)).isLessThan(ids.indexOf(second));
        assertThat(ids.indexOf(second)).isLessThan(ids.indexOf(first));
        assertThat(recent).allSatisfy(view -> {
            assertThat(view.steps()).isEmpty();
            assertThat(view.createTime()).isNotNull();
        });

        List<BatchExecutionView> top = launchService.recentExecutions(2);
        assertThat(top).hasSize(2);
        assertThat(top.get(0).executionId()).isEqualTo(third);
    }

    @Test
    @DisplayName("[limit이 범위를 벗어나면 1~50으로 보정한다]")
    void recentExecutions_clampsLimit() {
        assertThat(launchService.recentExecutions(0)).hasSizeLessThanOrEqualTo(1);
        assertThat(launchService.recentExecutions(10_000)).hasSizeLessThanOrEqualTo(50);
    }
}
