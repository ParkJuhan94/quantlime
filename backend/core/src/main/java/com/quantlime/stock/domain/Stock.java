package com.quantlime.stock.domain;

import com.quantlime.common.domain.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "stock",
    // DomesticListedStockCache.refresh()가 600초마다
    // findByListingStatusAndMarketTypeInAndPriceUnsupportedFalse로 이 두
    // 컬럼을 필터링한다 - price_unsupported는 대부분 false라 선택도가
    // 낮아 인덱스에 넣지 않고, 선택도 높은 listing_status/market_type만
    // 복합 인덱스로 잡는다. 성능 개선 계획 문서 Phase 2 참고.
    indexes = @Index(name = "idx_stock_listing_status_market_type",
        columnList = "listing_status, market_type")
)
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Stock extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long id;

    @Column(name = "stock_code", nullable = false, unique = true, length = 10)
    private String stockCode;

    @Column(name = "stock_name", nullable = false, length = 100)
    private String stockName;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", nullable = false, length = 10)
    private MarketType marketType;

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_status", nullable = false, length = 10)
    private ListingStatus listingStatus;

    @Column(name = "sector", length = 100)
    private String sector;

    // 해외 종목 전용(국내는 stockName 자체가 이미 한글이라 null로 둔다) -
    // KIS 마스터파일의 한글종목명 컬럼을 그대로 저장. 화면 표시는
    // getDisplayName()을 통해 이 값을 우선 사용한다.
    @Column(name = "korean_name", length = 100)
    private String koreanName;

    // 가격 소스(Toss 캔들/현재가 API)가 커버하지 않는 종목 표시. KONEX·
    // 스팩·상폐 잔존 코드처럼 조회 시 stock-not-found(404)만 반복하는
    // 종목을 최초 발견 시 true로 표시해, 이후 기동마다의 갭필/스윕 대상에서
    // 제외한다(매 기동 404 폭주·불필요한 쿼터 소모 방지). 기본 false.
    @Column(name = "price_unsupported", nullable = false)
    private boolean priceUnsupported = false;

    @Builder
    private Stock(String stockCode, String stockName, MarketType marketType,
                  ListingStatus listingStatus, String sector, String koreanName) {
        validateStock(stockCode, stockName, marketType, listingStatus);
        this.stockCode = stockCode;
        this.stockName = stockName;
        this.marketType = marketType;
        this.listingStatus = listingStatus;
        this.sector = sector;
        this.koreanName = koreanName;
    }

    public static Stock of(String stockCode, String stockName,
                           MarketType marketType, ListingStatus listingStatus,
                           String sector) {
        return of(stockCode, stockName, marketType, listingStatus, sector, null);
    }

    public static Stock of(String stockCode, String stockName,
                           MarketType marketType, ListingStatus listingStatus,
                           String sector, String koreanName) {
        return Stock.builder()
            .stockCode(stockCode)
            .stockName(stockName)
            .marketType(marketType)
            .listingStatus(listingStatus)
            .sector(sector)
            .koreanName(koreanName)
            .build();
    }

    // koreanName이 있으면(해외 종목) 그걸, 없으면(국내 종목) stockName을
    // 화면 표시명으로 쓴다 - 국내는 stockName 자체가 이미 한글이라 별도
    // 처리가 필요 없다.
    public String getDisplayName() {
        return (koreanName != null && !koreanName.isBlank()) ? koreanName : stockName;
    }

    public void updateListingStatus(ListingStatus listingStatus) {
        Assert.notNull(listingStatus, "상장 상태는 필수입니다.");
        this.listingStatus = listingStatus;
    }

    public void updateStockName(String stockName) {
        Assert.hasText(stockName, "종목명은 필수입니다.");
        this.stockName = stockName;
    }

    public void updateKoreanName(String koreanName) {
        this.koreanName = koreanName;
    }

    public void markPriceUnsupported() {
        this.priceUnsupported = true;
    }

    private void validateStock(String stockCode, String stockName,
                               MarketType marketType,
                               ListingStatus listingStatus) {
        Assert.hasText(stockCode, "종목 코드는 필수입니다.");
        Assert.hasText(stockName, "종목명은 필수입니다.");
        Assert.notNull(marketType, "시장 유형은 필수입니다.");
        Assert.notNull(listingStatus, "상장 상태는 필수입니다.");
    }
}
