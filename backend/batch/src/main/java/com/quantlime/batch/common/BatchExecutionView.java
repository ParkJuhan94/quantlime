package com.quantlime.batch.common;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;

/** 배치 실행 상태 조회 응답 - Step별 읽기/쓰기/skip 수로 어디까지 진행됐는지 보여준다. */
public record BatchExecutionView(
    long executionId,
    String jobName,
    String status,
    String exitCode,
    String exitDescription,
    LocalDateTime startTime,
    LocalDateTime endTime,
    List<StepView> steps) {

    public record StepView(String name, String status, long readCount, long writeCount, long skipCount,
                           long commitCount) {
    }

    public static BatchExecutionView from(JobExecution execution) {
        List<StepView> steps = execution.getStepExecutions().stream()
            .map(BatchExecutionView::toStepView)
            .toList();
        return new BatchExecutionView(
            execution.getId(),
            execution.getJobInstance().getJobName(),
            execution.getStatus().name(),
            execution.getExitStatus().getExitCode(),
            execution.getExitStatus().getExitDescription(),
            execution.getStartTime(),
            execution.getEndTime(),
            steps);
    }

    private static StepView toStepView(StepExecution step) {
        return new StepView(step.getStepName(), step.getStatus().name(), step.getReadCount(),
            step.getWriteCount(), step.getSkipCount(), step.getCommitCount());
    }
}
