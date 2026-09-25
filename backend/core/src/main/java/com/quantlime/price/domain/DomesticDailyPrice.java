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
import lombok.AccessLevel;
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
    )
    // idx_daily_price_stock_date(stock_code, trade_date DESC)는 uk_daily_price_stock_date와
    // 컬럼 구성이 완전히 같아 제거했다(2026-09 성능 감사 - 47MB, 2.3일간 읽기
    // 0회). MySQL은 오름차순 인덱스를 역방향으로도 스캔할 수 있어(EXPLAIN상
    // "Index lookup ... (reverse)") findTopByStockCodeOrderByTradeDateDesc류
    // 최신행 조회는 UK만으로 그대로 커버된다.
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

    // nullable(기존 행은 NULL) - ddl-auto: update로 자동 추가되는 컬럼이라
    // 과거 행을 소급 백필하지 않는 한 NULL로 남는다. NULL은 "미확정"으로
    // 취급해야 해 Lombok 기본 게터 대신 아래 isRegularCloseConfirmed()로만
    // 읽는다(2026-09-21, close_price를 정규장 종가로 전환하며 도입 -
    // ~/.claude/plans/dynamic-prancing-lerdorf.md 참고).
    @Getter(AccessLevel.NONE)
    @Column(name = "regular_close_confirmed")
    private Boolean regularCloseConfirmed;

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
     * 정규장 종가 캡처(15:35, {@code DomesticRegularCloseCaptureScheduler}) 시점에
     * 아직 오늘 행이 없을 때 쓰는 팩토리 - O/H/L=종가, 거래량=0인 임시값으로
     * 만들고, 뒤이은 16:00/20:10 일봉 배치({@code DomesticDailyPriceService
     * #upsertCandle})가 updateOhlcvKeepingClose로 실제 O/H/L/V만 채운다. 그
     * 사이 짧은 시간(보통 30분 이내) 차트/API가 이 임시값을 그대로 노출할 수
     * 있으나(시가=고가=저가=종가인 상태), 자동으로 정정되는 값이라 감수한다.
     */
    public static DomesticDailyPrice ofRegularCloseOnly(
            String stockCode, LocalDate tradeDate, Long closePrice) {
        DomesticDailyPrice price = DomesticDailyPrice.builder()
            .stockCode(stockCode)
            .tradeDate(tradeDate)
            .openPrice(closePrice)
            .highPrice(closePrice)
            .lowPrice(closePrice)
            .closePrice(closePrice)
            .volume(0L)
            .build();
        price.regularCloseConfirmed = true;
        return price;
    }

    /** NULL(기존 행/과거 컬럼 추가 이전)은 "미확정"으로 취급한다. */
    public boolean isRegularCloseConfirmed() {
        return Boolean.TRUE.equals(regularCloseConfirmed);
    }

    /**
     * 정규장(15:30) 마감 직후 캡처한 종가를 확정한다 - 오늘 행이 이미 있을
     * 때 {@code DomesticRegularCloseCaptureScheduler}가 쓴다. 이후
     * {@link #updateOhlcv}가 이 값을 다시 NXT 포함 값으로 덮어쓰지 않도록
     * 보호 플래그를 함께 세운다.
     */
    public void confirmRegularClose(Long closePrice) {
        Assert.notNull(closePrice, "정규장 종가는 필수입니다.");
        this.closePrice = closePrice;
        this.regularCloseConfirmed = true;
    }

    /**
     * 당일(장중) 백필로 미리 저장된 미완성 캔들을, 장 마감 후 배치가 받아온
     * 확정 캔들로 덮어쓸 때 사용한다. setter를 두지 않는 컨벤션을 지키기
     * 위한 비즈니스 메서드 - 과거 거래일 행은 이미 확정값이라 호출 대상이
     * 아니다(호출측에서 당일 거래일에 한해서만 사용).
     *
     * <p>close까지 포함해 NXT 반영 캔들로 완전히 덮어쓰는 메서드이므로, 호출
     * 시점에 정규장 종가 보호 플래그가 서 있었더라도 항상 해제한다 - 이
     * 메서드가 불리는 경우는 (1) 애초에 보호 대상이 아니었거나 (2) 수정주가
     * 소급 재조정(overwriteAll)처럼 과거 전체 가격이 비율로 바뀌어 보호된
     * 값 자체가 이미 틀려진 경우뿐이다. 보호된 값을 유지한 채 O/H/L/V만
     * 갱신하려면 {@link #updateOhlcvKeepingClose}를 쓴다.
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
        this.regularCloseConfirmed = false;
    }

    /**
     * {@link #updateOhlcv}와 달리 close/보호 플래그는 건드리지 않고 O/H/L/V만
     * 갱신한다 - 정규장 종가가 이미 확정된 행에 일봉 배치가 값을 채울 때 쓴다.
     */
    public void updateOhlcvKeepingClose(Long openPrice, Long highPrice, Long lowPrice, Long volume) {
        Assert.notNull(openPrice, "시가는 필수입니다.");
        Assert.notNull(highPrice, "고가는 필수입니다.");
        Assert.notNull(lowPrice, "저가는 필수입니다.");
        Assert.notNull(volume, "거래량은 필수입니다.");
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
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
