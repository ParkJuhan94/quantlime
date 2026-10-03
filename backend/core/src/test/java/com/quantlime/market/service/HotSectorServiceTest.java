package com.quantlime.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import com.quantlime.market.cache.MarketRankingCache;
import com.quantlime.market.dto.response.HotSectorResponse;
import com.quantlime.market.dto.response.MarketRankingResponse;
import com.quantlime.price.domain.StockLiquidity;
import com.quantlime.price.implement.StockLiquidityReader;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class HotSectorServiceTest {

    @Mock
    private MarketRankingCache domesticMarketRankingCache;

    @Mock
    private StockLiquidityReader stockLiquidityReader;

    @InjectMocks
    private HotSectorService service;

    private MarketRankingResponse item(String code, String sector, double changeRate) {
        return new MarketRankingResponse(code, "종목" + code, sector, 1000.0, changeRate, null, null, null, null, true,
            null, null, null);
    }

    private StockLiquidity liquidity(String code, double value, boolean liquid) {
        return StockLiquidity.of(code, LocalDate.of(2026, 10, 1), value, 0, liquid);
    }

    @Test
    @DisplayName("[섹터 등락률은 거래대금 가중 평균이라 대형주 변동이 더 크게 반영된다]")
    void getHotSectors_weightsByTradingValue() {
        // given: 반도체 - 대형주 +1%(가중 100), 소형주 +30%(가중 1)×2 → 단순평균은 20%대지만 가중평균은 약 1.6%
        given(domesticMarketRankingCache.getAll()).willReturn(List.of(
            item("A", "반도체", 1.0), item("B", "반도체", 30.0), item("C", "반도체", 30.0),
            item("D", "은행", 5.0), item("E", "은행", 5.0), item("F", "은행", 5.0)));
        given(stockLiquidityReader.findAllByStockCodes(anyList())).willReturn(List.of(
            liquidity("A", 100, true), liquidity("B", 1, true), liquidity("C", 1, true),
            liquidity("D", 10, true), liquidity("E", 10, true), liquidity("F", 10, true)));

        // when
        List<HotSectorResponse> result = service.getHotSectors(5);

        // then: 가중 평균 기준 은행(5.0) > 반도체(약 1.6)
        assertThat(result).extracting(HotSectorResponse::sector).containsExactly("은행", "반도체");
        assertThat(result.get(1).changeRate()).isBetween(1.5, 1.7);
        assertThat(result.get(1).leaders().get(0).stockCode()).isEqualTo("A");
    }

    @Test
    @DisplayName("[유동성 필터 탈락 종목과 종목 3개 미만 섹터는 집계에서 제외한다]")
    void getHotSectors_excludesIlliquidAndSmallSectors() {
        // given: 철강은 유동 종목이 2개뿐, 조선은 3개지만 1개가 비유동 → 2개로 줄어 둘 다 제외
        given(domesticMarketRankingCache.getAll()).willReturn(List.of(
            item("A", "철강", 3.0), item("B", "철강", 3.0),
            item("C", "조선", 9.0), item("D", "조선", 9.0), item("E", "조선", 9.0)));
        given(stockLiquidityReader.findAllByStockCodes(anyList())).willReturn(List.of(
            liquidity("A", 10, true), liquidity("B", 10, true),
            liquidity("C", 10, true), liquidity("D", 10, true), liquidity("E", 10, false)));

        // when & then
        assertThat(service.getHotSectors(5)).isEmpty();
    }

    @Test
    @DisplayName("[스냅샷이 비어 있으면(장 마감 후) 빈 목록을 반환한다]")
    void getHotSectors_emptySnapshot_returnsEmpty() {
        // given
        given(domesticMarketRankingCache.getAll()).willReturn(List.of());

        // when & then
        assertThat(service.getHotSectors(5)).isEmpty();
    }
}
