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

// 2026-08-01 daily_price -> domestic_daily_price 테이블 리네임(국내/해외
// 네이밍 일관성 정리, DomesticDailyPrice 클래스명과 짝) - unique
// constraint/index 이름(uk_daily_price_*, idx_daily_price_*)은 로컬 DB에서
// 수동 RENAME TABLE로 옮긴 기존 물리 제약과 이름이 그대로 일치해야
// ddl-auto: update가 "이미 존재함"으로 인식해 중복 생성을 안 하므로
// 일부러 안 바꿨다(테이블명만 바뀌고 제약 이름은 원래 그대로 보존됨).
@Entity
@Table(name = "domestic_daily_price",
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
public class DomesticDailyPrice extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_price_id")
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 10)
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
    private DomesticDailyPrice(String stockCode, LocalDate tradeDate,
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

    public static DomesticDailyPrice of(String stockCode, LocalDate tradeDate,
                                Long openPrice, Long highPrice,
                                Long lowPrice, Long closePrice,
                                Long volume) {
        return DomesticDailyPrice.builder()
            .stockCode(stockCode)
            .tradeDate(tradeDate)
            .openPrice(openPrice)
            .highPrice(highPrice)
            .lowPrice(lowPrice)
            .closePrice(closePrice)
            .volume(volume)
            .build();
    }

    /**
     * 당일(장중) 백필로 미리 저장된 미완성 캔들을, 장 마감 후 배치가 받아온
     * 확정 캔들로 덮어쓸 때 사용한다. setter를 두지 않는 컨벤션을 지키기
     * 위한 비즈니스 메서드 - 과거 거래일 행은 이미 확정값이라 호출 대상이
     * 아니다(호출측에서 당일 거래일에 한해서만 사용).
     */
    public void updateOhlcv(Long openPrice, Long highPrice, Long lowPrice,
                            Long closePrice, Long volume) {
        Assert.notNull(openPrice, "시가는 필수입니다.");
        Assert.notNull(highPrice, "고가는 필수입니다.");
        Assert.notNull(lowPrice, "저가는 필수입니다.");
        Assert.notNull(closePrice, "종가는 필수입니다.");
        Assert.notNull(volume, "거래량은 필수입니다.");
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
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
