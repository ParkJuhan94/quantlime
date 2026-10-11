package com.quantlime.backtest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quantlime.stock.domain.MarketType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class CrossSectionalBacktestResultTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 30);

    private CrossSectionalBacktestResult resultWith(List<BacktestBucket> buckets) {
        return CrossSectionalBacktestResult.of(MarketType.KOSPI, BacktestAxis.TREND, 20, "v3.0",
            BacktestSampleSplit.FULL, DATE, 900, 0.05, 0.01, 0.09, 250, 200_000,
            0.0, 0.01, -0.02, 0.02, buckets);
    }

    @Test
    @DisplayName("[생성 시 키와 통계 값을 그대로 담는다]")
    void of_keepsAllFields() {
        CrossSectionalBacktestResult result = resultWith(List.of(BacktestBucket.of(1, 0.01, 0.0, 0.5, 100)));

        assertThat(result.getMarketType()).isEqualTo(MarketType.KOSPI);
        assertThat(result.getAxis()).isEqualTo(BacktestAxis.TREND);
        assertThat(result.getHorizonDays()).isEqualTo(20);
        assertThat(result.getScoreVersion()).isEqualTo("v3.0");
        assertThat(result.getSampleSplit()).isEqualTo(BacktestSampleSplit.FULL);
        assertThat(result.getStockCount()).isEqualTo(900);
        assertThat(result.getMeanIc()).isEqualTo(0.05);
        assertThat(result.getNullPercentileHigh()).isEqualTo(0.02);
        assertThat(result.getBuckets()).hasSize(1);
    }

    @Test
    @DisplayName("[버킷 목록은 방어적으로 복사하고, null이면 빈 목록이다]")
    void of_copiesBucketsDefensively() {
        List<BacktestBucket> source = new ArrayList<>(List.of(BacktestBucket.of(1, 0.01, 0.0, 0.5, 100)));
        CrossSectionalBacktestResult result = resultWith(source);

        source.clear();

        assertThat(result.getBuckets()).hasSize(1);
        assertThat(resultWith(null).getBuckets()).isEmpty();
    }

    @Test
    @DisplayName("[재실행하면 새 행 대신 값과 버킷을 통째로 교체한다]")
    void updateFrom_replacesValuesAndBuckets() {
        CrossSectionalBacktestResult result = resultWith(List.of(BacktestBucket.of(1, 0.01, 0.0, 0.5, 100)));

        result.updateFrom(DATE.plusDays(1), 950, 0.07, 0.02, 0.12, 260, 210_000,
            null, null, null, null,
            List.of(BacktestBucket.of(1, 0.02, 0.01, 0.6, 120), BacktestBucket.of(2, 0.0, 0.0, 0.4, 130)));

        assertThat(result.getBacktestDate()).isEqualTo(DATE.plusDays(1));
        assertThat(result.getStockCount()).isEqualTo(950);
        assertThat(result.getMeanIc()).isEqualTo(0.07);
        assertThat(result.getNullMean()).isNull();
        assertThat(result.getBuckets()).extracting(BacktestBucket::getBucketNumber).containsExactly(1, 2);
    }

    @Test
    @DisplayName("[재실행 결과에 버킷이 없으면 기존 버킷을 비운다]")
    void updateFrom_nullBuckets_clearsExisting() {
        CrossSectionalBacktestResult result = resultWith(List.of(BacktestBucket.of(1, 0.01, 0.0, 0.5, 100)));

        result.updateFrom(DATE, 900, 0.05, 0.01, 0.09, 250, 200_000, null, null, null, null, null);

        assertThat(result.getBuckets()).isEmpty();
    }

    @Test
    @DisplayName("[필수 값(시장·축·버전·분할·실행일)이 비어 있으면 생성을 거부한다]")
    void of_missingRequiredValues_throws() {
        assertThatThrownBy(() -> CrossSectionalBacktestResult.of(null, BacktestAxis.TREND, 20, "v3.0",
            BacktestSampleSplit.FULL, DATE, 1, null, null, null, 1, 1, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CrossSectionalBacktestResult.of(MarketType.KOSPI, BacktestAxis.TREND, 20, " ",
            BacktestSampleSplit.FULL, DATE, 1, null, null, null, 1, 1, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CrossSectionalBacktestResult.of(MarketType.KOSPI, BacktestAxis.TREND, 20, "v3.0",
            BacktestSampleSplit.FULL, null, 1, null, null, null, 1, 1, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
