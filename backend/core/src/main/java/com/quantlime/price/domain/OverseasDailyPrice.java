package com.quantlime.price.domain;

import com.quantlime.common.domain.TimeBaseEntity;
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
 * 해외주식(NASDAQ/NYSE) 일별 OHLCV. {@link DailyPrice}와 별도 엔티티로 둔
 * 이유: 국내 원화는 정수 단위(Long)가 자연스럽지만 미국 달러 가격은
 * 소수점 단위(예: $317.31)라 Long 컬럼에 그대로 담으면 소수점이 잘려
 * 백테스트 수익률·거래대금 계산이 오염된다. 기존 DailyPrice의 컬럼
 * 타입을 바꾸면 국내 스코어링·차트 등 기존 호출부 전체에 영향이 커
 * (CLAUDE.md "기존 코드 변경 범위 원칙"), 대신 신규 엔티티로 분리했다.
 */
@Entity
@Table(name = "overseas_daily_price",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_overseas_daily_price_stock_date",
        columnNames = {"stock_code", "trade_date"}
    ),
    indexes = @Index(
        name = "idx_overseas_daily_price_stock_date",
        columnList = "stock_code, trade_date DESC"
    )
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class OverseasDailyPrice extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "overseas_daily_price_id")
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 6)
    private String stockCode;

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

    @Column(name = "volume", nullable = false)
    private Long volume;

    @Builder
    private OverseasDailyPrice(String stockCode, LocalDate tradeDate,
                               Double openPrice, Double highPrice, Double lowPrice,
                               Double closePrice, Long volume) {
        validateOverseasDailyPrice(stockCode, tradeDate, openPrice, highPrice,
            lowPrice, closePrice, volume);
        this.stockCode = stockCode;
        this.tradeDate = tradeDate;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
    }

    public static OverseasDailyPrice of(String stockCode, LocalDate tradeDate,
                                        Double openPrice, Double highPrice, Double lowPrice,
                                        Double closePrice, Long volume) {
        return OverseasDailyPrice.builder()
            .stockCode(stockCode)
            .tradeDate(tradeDate)
            .openPrice(openPrice)
            .highPrice(highPrice)
            .lowPrice(lowPrice)
            .closePrice(closePrice)
            .volume(volume)
            .build();
    }

    private void validateOverseasDailyPrice(String stockCode, LocalDate tradeDate,
                                            Double openPrice, Double highPrice,
                                            Double lowPrice, Double closePrice, Long volume) {
        Assert.hasText(stockCode, "종목 코드는 필수입니다.");
        Assert.notNull(tradeDate, "거래일은 필수입니다.");
        Assert.notNull(openPrice, "시가는 필수입니다.");
        Assert.notNull(highPrice, "고가는 필수입니다.");
        Assert.notNull(lowPrice, "저가는 필수입니다.");
        Assert.notNull(closePrice, "종가는 필수입니다.");
        Assert.notNull(volume, "거래량은 필수입니다.");
    }
}
