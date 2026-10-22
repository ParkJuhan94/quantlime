package com.quantlime.batch.common;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 기동 직후 두 가지를 한다.
 *
 * <ol>
 *   <li>메타 테이블 누락을 ERROR 로그와 게이지로 드러낸다. 앱은 계속 띄운다 - 배치 하나 때문에 사이트를 내리지 않는다.
 *   <li>서버가 죽어 STARTED로 남은 실행을 FAILED로 정리한다. 안 하면 restart가 "이미 실행 중"으로 막힌다. 백엔드가
 *       단일 인스턴스라는 전제다 - 인스턴스를 늘리면 다른 인스턴스에서 정상 실행 중인 Job을 실패로 만들 수 있다.
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchMetadataStartupChecker {

    private final BatchMetadataChecker metadataChecker;
    private final JobExplorer jobExplorer;
    private final JobRepository jobRepository;
    private final Clock clock;
    private final BatchFailureNotifier failureNotifier;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        List<String> missing = metadataChecker.findMissingTables();
        if (!missing.isEmpty()) {
            log.error("배치 메타 테이블 누락 - Job 실행이 거부됩니다: missing={}", missing);
            return;
        }
        for (String jobName : jobExplorer.getJobNames()) {
            for (JobExecution execution : jobExplorer.findRunningJobExecutions(jobName)) {
                markFailed(execution);
            }
        }
    }

    private void markFailed(JobExecution execution) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (StepExecution step : execution.getStepExecutions()) {
            if (step.getStatus().isRunning()) {
                step.setStatus(BatchStatus.FAILED);
                step.setExitStatus(ExitStatus.FAILED.addExitDescription("서버 재기동으로 중단됨"));
                step.setEndTime(now);
                jobRepository.update(step);
            }
        }
        execution.setStatus(BatchStatus.FAILED);
        execution.setExitStatus(ExitStatus.FAILED.addExitDescription("서버 재기동으로 중단됨"));
        execution.setEndTime(now);
        jobRepository.update(execution);
        log.warn("서버 재기동으로 중단된 배치 실행을 FAILED로 정리: jobName={}, executionId={}",
            execution.getJobInstance().getJobName(), execution.getId());
        failureNotifier.notifyInterrupted(execution);
    }
}
