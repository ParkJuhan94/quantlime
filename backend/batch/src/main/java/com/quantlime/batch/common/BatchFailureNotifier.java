package com.quantlime.batch.common;

import com.quantlime.infra.slack.SlackWebhookClient;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

/**
 * Job이 FAILED로 끝나면 운영 Slack 채널(DLT 알림과 같은 {@code sendOpsMessage})로 알린다. 실행 이력은 DB에 남지만 주간
 * 백테스트나 스코어 재구성처럼 사람이 지켜보지 않는 Job은 누가 조회하기 전에는 실패를 알 수 없어서다.
 *
 * <p>서버가 죽어 {@code afterJob}이 불리지 않은 실행은 재기동 때 {@link BatchMetadataStartupChecker}가 FAILED로 정리하면서
 * {@link #notifyInterrupted}로 같은 채널에 알린다(야간 00:00 중지에 걸린 경우 등).
 *
 * <p>Slack 전송 실패가 Job 결과를 바꾸면 안 되므로 예외는 삼키고 로그만 남긴다 - 실패 자체는 이미 메타 테이블에 기록돼 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchFailureNotifier implements JobExecutionListener {

    private static final int MAX_FAILED_STEPS = 5;
    private static final int MAX_REASON_LENGTH = 300;

    private final SlackWebhookClient slackWebhookClient;

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            send(buildMessage("[배치 실패]", jobExecution));
        }
    }

    /** 서버 재기동으로 중단된 실행을 FAILED로 정리한 직후 호출한다. */
    public void notifyInterrupted(JobExecution jobExecution) {
        send(buildMessage("[배치 중단]", jobExecution));
    }

    private String buildMessage(String title, JobExecution execution) {
        long executionId = execution.getId();
        StringBuilder message = new StringBuilder()
            .append(title).append(' ').append(execution.getJobInstance().getJobName())
            .append(" (executionId=").append(executionId).append(')');

        List<StepExecution> failedSteps = execution.getStepExecutions().stream()
            .filter(step -> step.getStatus() == BatchStatus.FAILED || step.getStatus().isRunning())
            .limit(MAX_FAILED_STEPS)
            .toList();
        for (StepExecution step : failedSteps) {
            message.append("\n- 실패 Step: ").append(step.getStepName())
                .append(" (read=").append(step.getReadCount())
                .append(", write=").append(step.getWriteCount())
                .append(", skip=").append(step.getSkipCount()).append(')');
        }
        reason(execution).ifPresent(reason -> message.append("\n- 사유: ").append(reason));
        message.append("\n- 이어서 실행: POST /api/admin/data/batch/executions/").append(executionId).append("/restart");
        return message.toString();
    }

    private static Optional<String> reason(JobExecution execution) {
        String text = execution.getAllFailureExceptions().stream()
            .map(Throwable::getMessage)
            .filter(Objects::nonNull)
            .findFirst()
            .orElseGet(() -> execution.getExitStatus().getExitDescription());
        if (text.isBlank()) {
            return Optional.empty();
        }
        String oneLine = text.replace('\n', ' ');
        return Optional.of(oneLine.length() > MAX_REASON_LENGTH
            ? oneLine.substring(0, MAX_REASON_LENGTH) + "..." : oneLine);
    }

    private void send(String message) {
        try {
            slackWebhookClient.sendOpsMessage(message);
        } catch (Exception e) {
            log.error("배치 실패 Slack 알림 전송 실패: message={}", message, e);
        }
    }
}
