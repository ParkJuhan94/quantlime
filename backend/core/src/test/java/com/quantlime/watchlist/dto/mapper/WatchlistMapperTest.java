package com.quantlime.watchlist.dto.mapper;

import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.User;
import com.quantlime.watchlist.WatchlistGroupFixture;
import com.quantlime.watchlist.domain.Watchlist;
import com.quantlime.watchlist.domain.WatchlistGroup;
import com.quantlime.watchlist.dto.response.WatchlistResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class WatchlistMapperTest {

    private final User user = UserFixture.createUser();
    private final WatchlistGroup group = WatchlistGroupFixture.createWatchlistGroup(user);

    @Test
    @DisplayName("[나스닥 관심종목은 한글명을 노출하고 .O 접미사가 붙은 로고 URL을 내려준다]")
    void toWatchlistResponse_nasdaqStock_usesKoreanNameAndSuffixedLogoUrl() {
        // given
        Stock stock = Stock.of("AAPL", "APPLE INC", MarketType.NASDAQ, ListingStatus.LISTED, "720", "애플");
        Watchlist watchlist = Watchlist.of(user, stock, group, 0);

        // when
        WatchlistResponse response = WatchlistMapper.toWatchlistResponse(watchlist);

        // then
        assertThat(response.stockName()).isEqualTo("애플");
        assertThat(response.logoUrl())
            .isEqualTo("https://ssl.pstatic.net/imgstock/fn/real/logo/png/stock/StockAAPL.O.png");
    }

    @Test
    @DisplayName("[국내 관심종목은 기존과 동일하게 종목코드 그대로 로고 URL을 만든다]")
    void toWatchlistResponse_domesticStock_buildsLogoUrlFromStockCode() {
        // given
        Stock stock = Stock.of("005930", "삼성전자", MarketType.KOSPI, ListingStatus.LISTED, "전기전자");
        Watchlist watchlist = Watchlist.of(user, stock, group, 0);

        // when
        WatchlistResponse response = WatchlistMapper.toWatchlistResponse(watchlist);

        // then
        assertThat(response.stockName()).isEqualTo("삼성전자");
        assertThat(response.logoUrl())
            .isEqualTo("https://ssl.pstatic.net/imgstock/fn/real/logo/png/stock/Stock005930.png");
    }
}
