package com.quantlab.market.domain;

import com.quantlab.common.domain.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static lombok.AccessLevel.PROTECTED;

/**
 * 국내/해외 지수의 일별 종가 이력. 백테스트 초과수익률(종목수익률 - 지수
 * 수익률) 계산의 벤치마크 기준선으로만 쓰인다 - 홈 화면 지수 위젯의
 * {@code IndexChartCache}(60초 TTL, 60일 상한, 영속 저장 안 함)와는 목적이
 * 다르다(그쪽은 실시간 표시용, 이쪽은 백테스트 재현성을 위한 영속 이력).
 */
@Entity
@Table(name = "benchmark_index",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_benchmark_index_code_date",
        columnNames = {"index_code", "trade_date"}
    ),
    indexes = @Index(
        name = "idx_benchmark_index_code_date",
        columnList = "index_code, trade_date DESC"
    )
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class BenchmarkIndex extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "benchmark_index_id")
    private Long id;

    @Column(name = "index_code", nullable = false, length = 20)
    private String indexCode;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "open_price", nullable = false)
    private Double openPrice;

    @Column(name = "high_price", nullable = false)
    private Double highPrice;

    @Column(name = "low_price", nullable = false)
    private Double lowPrice;

    @Column(name = "close_price", nullable = false)
    private Double closePrice;

    @Builder
    private BenchmarkIndex(String indexCode, LocalDate tradeDate,
                           Double openPrice, Double highPrice,
                           Double lowPrice, Double closePrice) {
        validateBenchmarkIndex(indexCode, tradeDate, openPrice, highPrice, lowPrice, closePrice);
        this.indexCode = indexCode;
        this.tradeDate = tradeDate;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
    }

    public static BenchmarkIndex of(String indexCode, LocalDate tradeDate,
                                    Double openPrice, Double highPrice,
                                    Double lowPrice, Double closePrice) {
        return BenchmarkIndex.builder()
            .indexCode(indexCode)
            .tradeDate(tradeDate)
            .openPrice(openPrice)
            .highPrice(highPrice)
            .lowPrice(lowPrice)
            .closePrice(closePrice)
            .build();
    }

    private void validateBenchmarkIndex(String indexCode, LocalDate tradeDate,
                                        Double openPrice, Double highPrice,
                                        Double lowPrice, Double closePrice) {
        Assert.hasText(indexCode, "지수 코드는 필수입니다.");
        Assert.notNull(tradeDate, "거래일은 필수입니다.");
        Assert.notNull(openPrice, "시가는 필수입니다.");
        Assert.notNull(highPrice, "고가는 필수입니다.");
        Assert.notNull(lowPrice, "저가는 필수입니다.");
        Assert.notNull(closePrice, "종가는 필수입니다.");
    }
}
