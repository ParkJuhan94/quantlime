package com.quantlime.price.domain;

import com.quantlime.common.domain.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static lombok.AccessLevel.PROTECTED;

/**
 * 종목별 유동성 스냅샷(최근 20거래일 일평균 거래대금·거래량 0인 날 수) -
 * 종목당 최신 1건만 유지하며(unique stock_code), 매 실행마다 덮어쓴다.
 *
 * <p>{@link Stock}에 파생 컬럼으로 붙이지 않은 이유: {@code Stock}은
 * 종목마스터 동기화(신규상장/상장폐지 등)가 별도로 관리하는 테이블이라
 * 파생 지표를 섞으면 동기화 로직과 얽힌다. 스코어 랭킹의 잡주(거래정지·
 * 초저유동성) 필터와 횡단면 정규화 모집단 결정에 쓰인다(2026-09 감사
 * 세션 - CLAUDE.md §10 참고).
 */
@Entity
@Table(name = "stock_liquidity",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_stock_liquidity_stock_code",
        columnNames = {"stock_code"}
    )
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class StockLiquidity extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_liquidity_id")
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 10, unique = true)
    private String stockCode;

    @Column(name = "as_of", nullable = false)
    private LocalDate asOf;

    @Column(name = "avg_trading_value_20d")
    private Double avgTradingValue20d;

    @Column(name = "zero_volume_days_20d", nullable = false)
    private int zeroVolumeDays20d;

    // 국내/해외는 거래대금 통화 단위(원/달러)가 달라 절대 컷 임계값을 여기
    // 저장 시점에 미리 적용해 판정 결과만 남긴다 - 랭킹 조회·횡단면 정규화
    // 모집단 결정 양쪽 모두 이 boolean 하나만 보면 되므로, 통화를 모르는
    // 쿼리 계층에서 국내/해외 임계값을 따로 분기할 필요가 없다(임계값은
    // StockLiquidityService 한 곳에서만 관리 - CLAUDE.md §10 감사 세션 참고).
    @Column(name = "liquid", nullable = false)
    private boolean liquid;

    @Builder
    private StockLiquidity(String stockCode, LocalDate asOf,
                            Double avgTradingValue20d, int zeroVolumeDays20d, boolean liquid) {
        validate(stockCode, asOf);
        this.stockCode = stockCode;
        this.asOf = asOf;
        this.avgTradingValue20d = avgTradingValue20d;
        this.zeroVolumeDays20d = zeroVolumeDays20d;
        this.liquid = liquid;
    }

    public static StockLiquidity of(String stockCode, LocalDate asOf,
                                     Double avgTradingValue20d, int zeroVolumeDays20d, boolean liquid) {
        return StockLiquidity.builder()
            .stockCode(stockCode)
            .asOf(asOf)
            .avgTradingValue20d(avgTradingValue20d)
            .zeroVolumeDays20d(zeroVolumeDays20d)
            .liquid(liquid)
            .build();
    }

    /**
     * 매 실행마다 최신 스냅샷으로 덮어쓴다 - setter를 두지 않는 컨벤션을
     * 지키기 위한 비즈니스 메서드.
     */
    public void updateFrom(LocalDate asOf, Double avgTradingValue20d, int zeroVolumeDays20d, boolean liquid) {
        this.asOf = asOf;
        this.avgTradingValue20d = avgTradingValue20d;
        this.zeroVolumeDays20d = zeroVolumeDays20d;
        this.liquid = liquid;
    }

    private void validate(String stockCode, LocalDate asOf) {
        Assert.hasText(stockCode, "종목 코드는 필수입니다.");
        Assert.notNull(asOf, "스냅샷 기준일은 필수입니다.");
    }
}
