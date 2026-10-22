package com.quantlime.batch.common;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;

/**
 * 배치 실행 상태 조회 응답 - Step별 읽기/쓰기/skip 수로 어디까지 진행됐는지 보여준다. 최근 실행 목록은 파티션이 많은 Job의 응답이
 * 커지지 않도록 {@link #summaryFrom}으로 Step 없이 돌려주고, Step 상세는 단건 조회({@link #from})로 가져간다.
 */
public record BatchExecutionView(
    long executionId,
    // 같은 파라미터로 이어서 실행한 실행들은 같은 JobInstance다 - 화면이 "이미 이어서 실행된 실패"를 가려내는 데 쓴다
    long jobInstanceId,
    String jobName,
    String status,
    String exitCode,
    String exitDescription,
    // 시작 직전(STARTING)에는 startTime이 비어 있어 목록 정렬·표시에 쓰는 기준 시각이다
    LocalDateTime createTime,
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
        return of(execution, steps);
    }

    /** Step 없이 요약만 만든다(목록용). */
    public static BatchExecutionView summaryFrom(JobExecution execution) {
        return of(execution, List.of());
    }

    private static BatchExecutionView of(JobExecution execution, List<StepView> steps) {
        return new BatchExecutionView(
            execution.getId(),
            execution.getJobInstance().getInstanceId(),
            execution.getJobInstance().getJobName(),
            execution.getStatus().name(),
            execution.getExitStatus().getExitCode(),
            execution.getExitStatus().getExitDescription(),
            execution.getCreateTime(),
            execution.getStartTime(),
            execution.getEndTime(),
            steps);
    }

    private static StepView toStepView(StepExecution step) {
        return new StepView(step.getStepName(), step.getStatus().name(), step.getReadCount(),
            step.getWriteCount(), step.getSkipCount(), step.getCommitCount());
    }
}
