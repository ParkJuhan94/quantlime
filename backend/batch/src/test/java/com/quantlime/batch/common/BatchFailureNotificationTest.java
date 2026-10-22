package com.quantlime.batch.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.batch.BatchIntegrationTestSupport;
import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Job이 FAILED로 끝났을 때만 운영 Slack으로 알리고, Slack 장애가 Job 결과를 바꾸지 않는지 검증한다. */
class BatchFailureNotificationTest extends BatchIntegrationTestSupport {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);

    private void stubDomesticStocks(int count) {
        List<Stock> stocks = new ArrayList<>();
        IntStream.range(0, count).forEach(i -> stocks.add(
            Stock.of("D%05d".formatted(i), "국내" + i, MarketType.KOSPI, ListingStatus.LISTED, null)));
        given(stockMasterService.getAllListedStocks()).willReturn(stocks);
    }

    /** 두 번째 청크에서 서킷 OPEN으로 실패하게 만든다. */
    private void failOnSecondChunk() {
        CallNotPermittedException open = CallNotPermittedException.createCallNotPermittedException(
            CircuitBreaker.ofDefaults("quant-engine-test"));
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) {
                throw open;
            }
            return null;
        }).when(scoreService).recalculateDomesticScoresChunk(anyList());
    }

    @Test
    @DisplayName("[Job이 FAILED로 끝나면 Job 이름·executionId·실패 Step·restart 경로를 담아 운영 Slack으로 알린다]")
    void failedJob_sendsOpsSlackMessage() {
        stubDomesticStocks(150);
        failOnSecondChunk();

        BatchExecutionView view = runToEnd(launchService.launchScoreRebuild(FROM));

        assertThat(view.status()).isEqualTo("FAILED");
        ArgumentCaptor<String> message = forClass(String.class);
        verify(slackWebhookClient, times(1)).sendOpsMessage(message.capture());
        assertThat(message.getValue())
            .contains("[배치 실패]", "scoreRebuildJob", "executionId=" + view.executionId(),
                "domesticScoreRecalcStep",
                "POST /api/admin/data/batch/executions/" + view.executionId() + "/restart");
    }

    @Test
    @DisplayName("[정상 완료된 Job은 알리지 않는다]")
    void completedJob_doesNotNotify() {
        stubDomesticStocks(3);

        BatchExecutionView view = runToEnd(launchService.launchScoreRebuild(FROM));

        assertThat(view.status()).isEqualTo("COMPLETED");
        verify(slackWebhookClient, never()).sendOpsMessage(anyString());
    }

    @Test
    @DisplayName("[Slack 전송이 실패해도 Job 결과는 그대로 FAILED로 기록된다]")
    void slackFailure_doesNotChangeJobResult() {
        stubDomesticStocks(150);
        failOnSecondChunk();
        doThrow(new IllegalStateException("slack down")).when(slackWebhookClient).sendOpsMessage(anyString());

        BatchExecutionView view = runToEnd(launchService.launchScoreRebuild(FROM));

        assertThat(view.status()).isEqualTo("FAILED");
        verify(slackWebhookClient, times(1)).sendOpsMessage(anyString());
    }
}
