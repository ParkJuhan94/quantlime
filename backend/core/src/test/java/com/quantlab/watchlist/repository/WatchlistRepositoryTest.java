package com.quantlab.watchlist.repository;

import com.quantlab.stock.StockFixture;
import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.repository.StockRepository;
import com.quantlab.support.DataJpaTestSupport;
import com.quantlab.user.UserFixture;
import com.quantlab.user.domain.User;
import com.quantlab.user.repository.UserRepository;
import com.quantlab.watchlist.WatchlistFixture;
import com.quantlab.watchlist.domain.Watchlist;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class WatchlistRepositoryTest extends DataJpaTestSupport {

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockRepository stockRepository;

    private User user;
    private Stock stock;

    @BeforeEach
    void setUp() {
        user = userRepository.save(UserFixture.createUser());
        stock = stockRepository.save(StockFixture.createStock());
    }

    @Test
    @DisplayName("[등록된 관심 종목 여부를 user/stockCode로 확인한다]")
    void existsByUserIdAndStockCode_registered_returnsTrue() {
        // given
        watchlistRepository.save(WatchlistFixture.createWatchlist(user, stock));

        // when
        boolean exists = watchlistRepository.existsByUser_IdAndStock_StockCode(
            user.getId(), stock.getStockCode());

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("[다른 사용자의 관심 종목은 조회되지 않는다]")
    void existsByUserIdAndStockCode_otherUser_returnsFalse() {
        // given
        User otherUser = userRepository.save(
            UserFixture.createUser(user.getProvider(), "other-provider-id"));
        watchlistRepository.save(WatchlistFixture.createWatchlist(otherUser, stock));

        // when
        boolean exists = watchlistRepository.existsByUser_IdAndStock_StockCode(
            user.getId(), stock.getStockCode());

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("[findAllWithStockByUserId는 stock을 함께 조회하고 최신순으로 정렬한다]")
    void findAllWithStockByUserId_returnsOrderedListWithStock() {
        // given
        Stock stock2 = stockRepository.save(StockFixture.createStock("000660", "SK하이닉스"));
        watchlistRepository.save(WatchlistFixture.createWatchlist(user, stock));
        watchlistRepository.save(WatchlistFixture.createWatchlist(user, stock2));

        // when
        List<Watchlist> result = watchlistRepository.findAllWithStockByUserId(user.getId());

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStock().getStockName()).isEqualTo("SK하이닉스");
        assertThat(result.get(1).getStock().getStockName()).isEqualTo("삼성전자");
    }

    @Test
    @DisplayName("[등록되지 않은 조합은 빈 값을 반환한다]")
    void findByUserIdAndStockCode_notFound_returnsEmpty() {
        // when
        Optional<Watchlist> found = watchlistRepository.findByUser_IdAndStock_StockCode(
            user.getId(), stock.getStockCode());

        // then
        assertThat(found).isEmpty();
    }
}
