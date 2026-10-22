package com.quantlime.batch.common;

import com.quantlime.batch.score.ScoreRebuildJobConfig;
import com.quantlime.common.exception.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * 배치 Job을 비동기로 시작/재시작한다. 호출자(관리자 API)는 executionId만 받아 바로 돌아가고 진행 상황은 조회 API로 본다 -
 * 스코어 재구성은 1.5시간이 걸려 요청 하나로 붙잡을 수 없다.
 *
 * <p>같은 이름의 Job이 이미 실행 중이면 파라미터가 달라도 시작하지 않는다. Boot가 만드는 기본 JobLauncher 대신 전용
 * 비동기 런처를 이 클래스 안에서만 쓴다 - 런처나 Executor를 빈으로 등록하면 Boot의 기본 자동 구성(applicationTaskExecutor,
 * jobLauncher)이 물러나 다른 곳의 동작이 바뀐다.
 */
@Slf4j
@Service
public class BatchJobLaunchService {

    private final JobExplorer jobExplorer;
    private final Job scoreRebuildJob;
    private final BatchMetadataChecker metadataChecker;
    private final Clock clock;
    private final TaskExecutorJobLauncher jobLauncher;

    public BatchJobLaunchService(JobRepository jobRepository, JobExplorer jobExplorer, Job scoreRebuildJob,
                                 BatchMetadataChecker metadataChecker, Clock clock) {
        this.jobExplorer = jobExplorer;
        this.scoreRebuildJob = scoreRebuildJob;
        this.metadataChecker = metadataChecker;
        this.clock = clock;
        this.jobLauncher = new TaskExecutorJobLauncher();
        this.jobLauncher.setJobRepository(jobRepository);
        this.jobLauncher.setTaskExecutor(new SimpleAsyncTaskExecutor("batch-launcher-"));
        try {
            this.jobLauncher.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException("배치 런처 초기화 실패", e);
        }
    }

    /** from 이후 스코어를 지우고 전종목을 다시 계산하는 Job을 시작한다. 항상 새 JobInstance다. */
    public long launchScoreRebuild(LocalDate from) {
        guardLaunchable(ScoreRebuildJobConfig.JOB_NAME);
        JobParameters parameters = new JobParametersBuilder()
            .addString(ScoreRebuildJobConfig.PARAM_FROM, from.toString())
            .addLong("requestedAt", clock.millis())
            .toJobParameters();
        return run(scoreRebuildJob, parameters);
    }

    /** 실패/중단된 실행을 같은 파라미터로 다시 시작한다 - 완료된 Step은 건너뛰고 끊긴 청크부터 이어 간다. */
    public long restart(long executionId) {
        JobExecution previous = requireExecution(executionId);
        BatchStatus status = previous.getStatus();
        if (status != BatchStatus.FAILED && status != BatchStatus.STOPPED) {
            throw new BatchLaunchRejectedException("재시작할 수 없는 상태입니다: executionId=%d, status=%s"
                .formatted(executionId, status));
        }
        String jobName = previous.getJobInstance().getJobName();
        if (!ScoreRebuildJobConfig.JOB_NAME.equals(jobName)) {
            throw new BatchLaunchRejectedException("지원하지 않는 Job입니다: " + jobName);
        }
        guardLaunchable(jobName);
        return run(scoreRebuildJob, previous.getJobParameters());
    }

    public BatchExecutionView getExecution(long executionId) {
        return BatchExecutionView.from(requireExecution(executionId));
    }

    private JobExecution requireExecution(long executionId) {
        JobExecution execution = jobExplorer.getJobExecution(executionId);
        if (execution == null) {
            throw new NotFoundException(BatchErrorCode.NOT_FOUND_EXECUTION);
        }
        return execution;
    }

    private void guardLaunchable(String jobName) {
        metadataChecker.requireComplete();
        if (!jobExplorer.findRunningJobExecutions(jobName).isEmpty()) {
            throw new BatchLaunchRejectedException("이미 실행 중인 배치가 있습니다: " + jobName);
        }
    }

    private long run(Job job, JobParameters parameters) {
        try {
            JobExecution execution = jobLauncher.run(job, parameters);
            log.info("배치 Job 시작: jobName={}, executionId={}", job.getName(), execution.getId());
            return execution.getId();
        } catch (Exception e) {
            throw new BatchLaunchRejectedException("배치 Job을 시작하지 못했습니다: " + e.getMessage(), e);
        }
    }
}
