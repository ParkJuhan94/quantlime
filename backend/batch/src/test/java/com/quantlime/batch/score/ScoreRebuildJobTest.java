package com.quantlime.batch.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.batch.BatchIntegrationTestSupport;
import com.quantlime.batch.common.BatchExecutionView;
import com.quantlime.batch.common.BatchLaunchRejectedException;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 삭제 1회 보장, 청크 분할, restart 이어하기, skip 격리, 중복 실행 거부를 검증한다. ScoreService/ScoreAppender는 mock이라
 * quant-engine과 스코어 테이블은 쓰지 않는다.
 */
class ScoreRebuildJobTest extends BatchIntegrationTestSupport {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);

    @Test
    @DisplayName("[정상 실행: 삭제 1회, 국내 150종목은 2청크, 해외 3종목은 1청크로 재계산하고 COMPLETED]")
    void completes_deletesOnce_andChunks() {
        stubListedStocks(150, 3);

        BatchExecutionView view = runToEnd(launchService.launchScoreRebuild(FROM));

        assertThat(view.status()).isEqualTo("COMPLETED");
        verify(scoreAppender, times(1)).deleteFrom(FROM);
        verify(scoreService, times(2)).recalculateDomesticScoresChunk(anyList());
        verify(scoreService, times(1)).recalculateOverseasScoresChunk(anyList());
        assertThat(view.steps()).extracting(BatchExecutionView.StepView::name)
            .containsExactly("scoreRebuildDeleteStep", "domesticScoreRecalcStep", "overseasScoreRecalcStep");
    }

    @Test
    @DisplayName("[서킷 OPEN으로 두 번째 청크가 실패하면 FAILED, restart는 삭제를 다시 하지 않고 두 번째 청크부터 이어서 COMPLETED]")
    void restart_skipsCompletedDeleteStep_andResumesFromFailedChunk() {
        stubListedStocks(150, 0);
        CallNotPermittedException open = CallNotPermittedException.createCallNotPermittedException(
            CircuitBreaker.ofDefaults("quant-engine-test"));
        List<List<String>> domesticCalls = new ArrayList<>();
        // 두 번째 호출(두 번째 청크)에서만 한 번 실패한다
        int[] callCount = {0};
        doAnswer(invocation -> {
            callCount[0]++;
            domesticCalls.add(invocation.getArgument(0));
            if (callCount[0] == 2) {
                throw open;
            }
            return null;
        }).when(scoreService).recalculateDomesticScoresChunk(anyList());

        BatchExecutionView failed = runToEnd(launchService.launchScoreRebuild(FROM));
        assertThat(failed.status()).isEqualTo("FAILED");

        BatchExecutionView restarted = runToEnd(launchService.restart(failed.executionId()));

        assertThat(restarted.status()).isEqualTo("COMPLETED");
        // 삭제는 처음 한 번뿐이다 - 이게 이번 전환의 핵심 보장
        verify(scoreAppender, times(1)).deleteFrom(FROM);
        // 첫 실행: 청크1 성공 + 청크2 실패, restart: 청크2만 다시 = 총 3회, 마지막 두 호출은 같은 두 번째 청크
        assertThat(domesticCalls).hasSize(3);
        assertThat(domesticCalls.get(2)).isEqualTo(domesticCalls.get(1));
        assertThat(domesticCalls.get(0)).doesNotContainAnyElementsOf(domesticCalls.get(1));
    }

    @Test
    @DisplayName("[한 종목만 계속 실패하면 청크를 종목 단위로 다시 써 그 종목만 skip하고 COMPLETED]")
    void failingStock_isSkippedIndividually() {
        stubListedStocks(3, 0);
        doThrow(new IllegalStateException("bad stock")).when(scoreService)
            .recalculateDomesticScoresChunk(org.mockito.ArgumentMatchers.argThat(codes -> codes.contains("D00001")));

        BatchExecutionView view = runToEnd(launchService.launchScoreRebuild(FROM));

        assertThat(view.status()).isEqualTo("COMPLETED");
        BatchExecutionView.StepView domestic = view.steps().stream()
            .filter(step -> step.name().equals("domesticScoreRecalcStep")).findFirst().orElseThrow();
        assertThat(domestic.skipCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[실행 중에 같은 Job을 또 시작하면 거부한다]")
    void secondLaunch_whileRunning_isRejected() throws Exception {
        stubListedStocks(1, 0);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            release.await(30, TimeUnit.SECONDS);
            return null;
        }).when(scoreService).recalculateDomesticScoresChunk(anyList());

        long executionId = launchService.launchScoreRebuild(FROM);
        try {
            assertThat(entered.await(30, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> launchService.launchScoreRebuild(FROM))
                .isInstanceOf(BatchLaunchRejectedException.class);
        } finally {
            release.countDown();
        }
        assertThat(runToEnd(executionId).status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("[COMPLETED 실행은 restart할 수 없다]")
    void restart_completedExecution_isRejected() {
        stubListedStocks(1, 0);
        BatchExecutionView view = runToEnd(launchService.launchScoreRebuild(FROM));

        assertThatThrownBy(() -> launchService.restart(view.executionId()))
            .isInstanceOf(BatchLaunchRejectedException.class);
        verify(scoreAppender, times(1)).deleteFrom(any());
    }

    private void stubListedStocks(int domesticCount, int overseasCount) {
        List<Stock> stocks = new ArrayList<>();
        IntStream.range(0, domesticCount).forEach(i -> stocks.add(
            Stock.of("D%05d".formatted(i), "국내" + i, MarketType.KOSPI, ListingStatus.LISTED, null)));
        IntStream.range(0, overseasCount).forEach(i -> stocks.add(
            Stock.of("O%05d".formatted(i), "해외" + i, MarketType.NASDAQ, ListingStatus.LISTED, null)));
        given(stockMasterService.getAllListedStocks()).willReturn(stocks);
    }
}
