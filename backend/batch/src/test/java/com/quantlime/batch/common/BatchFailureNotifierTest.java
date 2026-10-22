package com.quantlime.batch.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.infra.slack.SlackWebhookClient;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BatchFailureNotifierTest {

    @Mock
    private SlackWebhookClient slackWebhookClient;

    @InjectMocks
    private BatchFailureNotifier notifier;

    private JobExecution execution(BatchStatus status, BatchStatus stepStatus) {
        JobExecution execution = new JobExecution(new JobInstance(1L, "backtestWeeklyJob"), 9L, new JobParameters());
        execution.setStatus(status);
        StepExecution step = new StepExecution("backtestUniverseWorkerStep:universe1", execution);
        step.setStatus(stepStatus);
        step.setReadCount(4);
        step.setWriteCount(3);
        step.setWriteSkipCount(1);
        execution.addStepExecutions(List.of(step));
        return execution;
    }

    @Test
    @DisplayName("[FAILED 실행은 실패한 Step의 read/write/skip 수와 사유 첫 줄을 한 메시지에 담는다]")
    void afterJob_failed_buildsMessage() {
        JobExecution execution = execution(BatchStatus.FAILED, BatchStatus.FAILED);
        execution.addFailureException(new IllegalStateException("엔진 서킷 OPEN\n두 번째 줄"));

        notifier.afterJob(execution);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(slackWebhookClient).sendOpsMessage(message.capture());
        assertThat(message.getValue())
            .startsWith("[배치 실패] backtestWeeklyJob (executionId=9)")
            .contains("backtestUniverseWorkerStep:universe1 (read=4, write=3, skip=1)")
            .contains("사유: 엔진 서킷 OPEN 두 번째 줄")
            .contains("POST /api/admin/data/batch/executions/9/restart");
    }

    @Test
    @DisplayName("[COMPLETED·STOPPED 실행은 알리지 않는다]")
    void afterJob_notFailed_doesNotNotify() {
        notifier.afterJob(execution(BatchStatus.COMPLETED, BatchStatus.COMPLETED));
        notifier.afterJob(execution(BatchStatus.STOPPED, BatchStatus.STOPPED));

        verify(slackWebhookClient, never()).sendOpsMessage(anyString());
    }

    @Test
    @DisplayName("[서버 재기동으로 중단된 실행은 제목이 '배치 중단'이고 실행 중이던 Step을 표시한다]")
    void notifyInterrupted_marksRunningStep() {
        JobExecution execution = execution(BatchStatus.FAILED, BatchStatus.STARTED);
        execution.setExitStatus(ExitStatus.FAILED.addExitDescription("서버 재기동으로 중단됨"));

        notifier.notifyInterrupted(execution);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(slackWebhookClient).sendOpsMessage(message.capture());
        assertThat(message.getValue()).startsWith("[배치 중단]")
            .contains("backtestUniverseWorkerStep:universe1")
            .contains("사유: ")
            .contains("서버 재기동으로 중단됨");
    }

    @Test
    @DisplayName("[Slack 전송이 예외를 던져도 알림 호출은 예외를 전파하지 않는다]")
    void sendFailure_isSwallowed() {
        doThrow(new IllegalStateException("slack down")).when(slackWebhookClient).sendOpsMessage(anyString());

        notifier.afterJob(execution(BatchStatus.FAILED, BatchStatus.FAILED));
    }
}
