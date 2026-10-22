package com.quantlime.batch.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BatchMetadataCheckerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SimpleMeterRegistry meterRegistry;
    private BatchMetadataChecker checker;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        checker = new BatchMetadataChecker(jdbcTemplate, meterRegistry);
    }

    @Test
    @DisplayName("[테이블이 하나라도 없으면 실행을 거부하고, 누락 개수를 게이지로 노출한다]")
    void missingTable_isRejected_andGaugeShowsCount() {
        List<String> existing = new ArrayList<>(BatchMetadataChecker.REQUIRED_TABLES);
        existing.remove("BATCH_STEP_EXECUTION_CONTEXT");
        given(jdbcTemplate.queryForList(anyString(), eq(String.class))).willReturn(existing);

        assertThatThrownBy(checker::requireComplete)
            .isInstanceOf(BatchLaunchRejectedException.class)
            .hasMessageContaining("BATCH_STEP_EXECUTION_CONTEXT");
        assertThat(meterRegistry.get("quantlime.batch.metadata.missing.tables").gauge().value()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("[테이블이 모두 있으면 통과하고, 대소문자가 달라도 같은 테이블로 본다]")
    void allPresent_passes_caseInsensitive() {
        List<String> lower = BatchMetadataChecker.REQUIRED_TABLES.stream().map(String::toLowerCase).toList();
        given(jdbcTemplate.queryForList(anyString(), eq(String.class))).willReturn(lower);

        assertThatCode(checker::requireComplete).doesNotThrowAnyException();
        assertThat(meterRegistry.get("quantlime.batch.metadata.missing.tables").gauge().value()).isZero();
    }
}
