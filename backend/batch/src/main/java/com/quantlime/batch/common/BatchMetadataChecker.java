package com.quantlime.batch.common;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Spring Batch 메타 테이블 9개가 모두 있는지 확인한다. Boot의 {@code initialize-schema=always}는 스크립트 오류를 무시하는
 * {@code continueOnError} 모드로 실행되므로, 권한 부족 같은 실제 실패가 기동 시에는 드러나지 않고 Job을 실행할 때에야 터진다.
 * 삭제 단계로 시작하는 Job에서는 그 시점이 이미 데이터를 지운 뒤일 수 있어, 기동 때와 Job 시작 직전에 두 번 검사한다.
 */
@Slf4j
@Component
public class BatchMetadataChecker {

    static final List<String> REQUIRED_TABLES = List.of(
        "BATCH_JOB_INSTANCE", "BATCH_JOB_EXECUTION", "BATCH_JOB_EXECUTION_PARAMS",
        "BATCH_JOB_EXECUTION_CONTEXT", "BATCH_STEP_EXECUTION", "BATCH_STEP_EXECUTION_CONTEXT",
        "BATCH_JOB_SEQ", "BATCH_JOB_EXECUTION_SEQ", "BATCH_STEP_EXECUTION_SEQ");

    private final JdbcTemplate jdbcTemplate;
    private final AtomicInteger missingCount = new AtomicInteger();

    public BatchMetadataChecker(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        Gauge.builder("quantlime.batch.metadata.missing.tables", missingCount, AtomicInteger::get)
            .description("누락된 Spring Batch 메타 테이블 수 - 0이 아니면 Job 실행이 거부된다")
            .register(meterRegistry);
    }

    /** 없는 테이블 이름을 돌려주고 게이지를 갱신한다. 비어 있으면 정상이다. */
    public List<String> findMissingTables() {
        List<String> existing = jdbcTemplate.queryForList(
            "select table_name from information_schema.tables where table_schema = database()", String.class);
        // 운영체제에 따라 MySQL이 테이블명 대소문자를 다르게 보고하므로 대문자로 맞춰 비교한다
        Set<String> existingUpper = new HashSet<>();
        for (String name : existing) {
            existingUpper.add(name.toUpperCase(Locale.ROOT));
        }
        List<String> missing = REQUIRED_TABLES.stream().filter(table -> !existingUpper.contains(table)).toList();
        missingCount.set(missing.size());
        return missing;
    }

    /** Job 시작 직전 검사 - 누락이 있으면 삭제 단계가 실행되기 전에 거부한다. */
    public void requireComplete() {
        List<String> missing = findMissingTables();
        if (!missing.isEmpty()) {
            log.error("배치 메타 테이블 누락으로 실행 거부: missing={}", missing);
            throw new BatchLaunchRejectedException("배치 메타 테이블이 없어 실행할 수 없습니다: " + missing);
        }
    }
}
