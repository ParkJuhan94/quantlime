package com.quantlime.tradingflow.domain;

import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 투자자 분류(개인/외국인/기관/기타법인)의 하루 매수·매도·순매수 거래량(주, 정수). {@link StockInvestorTrading}이
 * 분류마다 {@code @AttributeOverride}로 컬럼 이름을 바꿔 4번 포함한다. 순매수는 토스가 준 값을 그대로 저장한다
 * (매수 − 매도, 음수면 순매도).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = PROTECTED)
public class InvestorVolume {

    @Column(name = "buy_volume")
    private Long buyVolume;

    @Column(name = "sell_volume")
    private Long sellVolume;

    @Column(name = "net_buy_volume")
    private Long netBuyVolume;

    @Builder
    private InvestorVolume(Long buyVolume, Long sellVolume, Long netBuyVolume) {
        this.buyVolume = buyVolume;
        this.sellVolume = sellVolume;
        this.netBuyVolume = netBuyVolume;
    }

    public static InvestorVolume of(long buyVolume, long sellVolume, long netBuyVolume) {
        return InvestorVolume.builder()
            .buyVolume(buyVolume)
            .sellVolume(sellVolume)
            .netBuyVolume(netBuyVolume)
            .build();
    }
}
