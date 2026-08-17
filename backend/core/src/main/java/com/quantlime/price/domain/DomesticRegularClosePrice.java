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
 * 국내 종목의 "정규장(09:00~15:30) 종가" 스냅샷. {@code domestic_daily_price
 * .close_price}는 NXT 프리/애프터마켓까지 반영해 20:00까지 계속 갱신되는
 * 값이라(20:10 재확정 배치 참고) NXT로 거래되는 종목의 "전일종가"로 그대로
 * 쓰면 등락률이 정규장 기준(다른 증권사 앱이 쓰는 관례적 기준)과 어긋난다
 * (2026-08-17 발견 - NXT 애프터마켓이 있는 종목만 등락률이 틀리고, 전통적인
 * 시간외 단일가만 있는 종목은 그 세션이 종가 필드에 안 섞여 문제가 없었음).
 *
 * <p>이 테이블은 그 정규장 종가만 별도로 영속화한다 - {@code
 * DomesticRegularCloseCaptureScheduler}가 매일 15:30에 그 시점 Redis 시세
 * 스냅샷을 그대로 저장한다. {@code domestic_daily_price}의 기존 upsert/
 * 재확정 로직(OHLCV 전체를 다루는 배치)과 완전히 분리해, 그 로직을 건드리지
 * 않고 이 값만 독립적으로 채운다.
 */
@Entity
@Table(name = "domestic_regular_close_price",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_domestic_regular_close_stock_date",
        columnNames = {"stock_code", "trade_date"}
    ),
    indexes = @Index(
        name = "idx_domestic_regular_close_stock_date",
        columnList = "stock_code, trade_date DESC"
    )
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class DomesticRegularClosePrice extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "domestic_regular_close_price_id")
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "close_price", nullable = false)
    private Long closePrice;

    @Builder
    private DomesticRegularClosePrice(String stockCode, LocalDate tradeDate, Long closePrice) {
        Assert.hasText(stockCode, "종목 코드는 필수입니다.");
        Assert.notNull(tradeDate, "거래일은 필수입니다.");
        Assert.notNull(closePrice, "종가는 필수입니다.");
        this.stockCode = stockCode;
        this.tradeDate = tradeDate;
        this.closePrice = closePrice;
    }

    public static DomesticRegularClosePrice of(String stockCode, LocalDate tradeDate, Long closePrice) {
        return DomesticRegularClosePrice.builder()
            .stockCode(stockCode)
            .tradeDate(tradeDate)
            .closePrice(closePrice)
            .build();
    }
}
