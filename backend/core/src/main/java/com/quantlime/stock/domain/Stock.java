package com.quantlime.stock.domain;

import com.quantlime.common.domain.TimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Stock extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long id;

    @Column(name = "stock_code", nullable = false, unique = true, length = 6)
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

    @Builder
    private Stock(String stockCode, String stockName, MarketType marketType,
                  ListingStatus listingStatus, String sector) {
        validateStock(stockCode, stockName, marketType, listingStatus);
        this.stockCode = stockCode;
        this.stockName = stockName;
        this.marketType = marketType;
        this.listingStatus = listingStatus;
        this.sector = sector;
    }

    public static Stock of(String stockCode, String stockName,
                           MarketType marketType, ListingStatus listingStatus,
                           String sector) {
        return Stock.builder()
            .stockCode(stockCode)
            .stockName(stockName)
            .marketType(marketType)
            .listingStatus(listingStatus)
            .sector(sector)
            .build();
    }

    public void updateListingStatus(ListingStatus listingStatus) {
        Assert.notNull(listingStatus, "상장 상태는 필수입니다.");
        this.listingStatus = listingStatus;
    }

    public void updateStockName(String stockName) {
        Assert.hasText(stockName, "종목명은 필수입니다.");
        this.stockName = stockName;
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
