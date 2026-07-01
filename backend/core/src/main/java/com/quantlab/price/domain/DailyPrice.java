package com.quantlab.price.domain;

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

@Entity
@Table(name = "daily_price",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_daily_price_stock_date",
        columnNames = {"stock_code", "trade_date"}
    ),
    indexes = @Index(
        name = "idx_daily_price_stock_date",
        columnList = "stock_code, trade_date DESC"
    )
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class DailyPrice extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_price_id")
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 6)
    private String stockCode;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "open_price", nullable = false)
    private Long openPrice;

    @Column(name = "high_price", nullable = false)
    private Long highPrice;

    @Column(name = "low_price", nullable = false)
    private Long lowPrice;

    @Column(name = "close_price", nullable = false)
    private Long closePrice;

    @Column(name = "volume", nullable = false)
    private Long volume;

    @Builder
    private DailyPrice(String stockCode, LocalDate tradeDate,
                       Long openPrice, Long highPrice, Long lowPrice,
                       Long closePrice, Long volume) {
        validateDailyPrice(stockCode, tradeDate, openPrice, highPrice,
            lowPrice, closePrice, volume);
        this.stockCode = stockCode;
        this.tradeDate = tradeDate;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
    }

    public static DailyPrice of(String stockCode, LocalDate tradeDate,
                                Long openPrice, Long highPrice,
                                Long lowPrice, Long closePrice,
                                Long volume) {
        return DailyPrice.builder()
            .stockCode(stockCode)
            .tradeDate(tradeDate)
            .openPrice(openPrice)
            .highPrice(highPrice)
            .lowPrice(lowPrice)
            .closePrice(closePrice)
            .volume(volume)
            .build();
    }

    private void validateDailyPrice(String stockCode, LocalDate tradeDate,
                                    Long openPrice, Long highPrice,
                                    Long lowPrice, Long closePrice,
                                    Long volume) {
        Assert.hasText(stockCode, "종목 코드는 필수입니다.");
        Assert.notNull(tradeDate, "거래일은 필수입니다.");
        Assert.notNull(openPrice, "시가는 필수입니다.");
        Assert.notNull(highPrice, "고가는 필수입니다.");
        Assert.notNull(lowPrice, "저가는 필수입니다.");
        Assert.notNull(closePrice, "종가는 필수입니다.");
        Assert.notNull(volume, "거래량은 필수입니다.");
    }
}
