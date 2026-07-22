package com.quantlime.watchlist.repository;

import com.quantlime.stock.StockFixture;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.repository.StockRepository;
import com.quantlime.support.DataJpaTestSupport;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import com.quantlime.watchlist.WatchlistFixture;
import com.quantlime.watchlist.WatchlistGroupFixture;
import com.quantlime.watchlist.domain.Watchlist;
import com.quantlime.watchlist.domain.WatchlistGroup;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class WatchlistRepositoryTest extends DataJpaTestSupport {

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private WatchlistGroupRepository watchlistGroupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User user;
    private Stock stock;
    private WatchlistGroup group;

    @BeforeEach
    void setUp() {
        user = userRepository.save(UserFixture.createUser());
        stock = stockRepository.save(StockFixture.createStock());
        group = watchlistGroupRepository.save(WatchlistGroupFixture.createWatchlistGroup(user));
    }

    @Test
    @DisplayName("[등록된 관심 종목 여부를 user/stockCode로 확인한다]")
    void existsByUserIdAndStockCode_registered_returnsTrue() {
        // given
        watchlistRepository.save(WatchlistFixture.createWatchlist(user, stock, group));

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
        WatchlistGroup otherGroup = watchlistGroupRepository.save(
            WatchlistGroupFixture.createWatchlistGroup(otherUser));
        watchlistRepository.save(WatchlistFixture.createWatchlist(otherUser, stock, otherGroup));

        // when
        boolean exists = watchlistRepository.existsByUser_IdAndStock_StockCode(
            user.getId(), stock.getStockCode());

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("[findAllWithStockByUserId는 stock을 함께 조회하고 sortOrder(동률이면 id)순으로 정렬한다]")
    void findAllWithStockByUserId_returnsOrderedListWithStock() {
        // given: 그룹 기능 도입 후 정렬 기준이 등록시각 최신순에서 사용자가
        // 직접 조정 가능한 sortOrder 기준으로 바뀌었다 - 픽스처는 항상
        // sortOrder=0이라 동률이면 id(등록 순서)가 다음 기준이 된다.
        Stock stock2 = stockRepository.save(StockFixture.createStock("000660", "SK하이닉스"));
        watchlistRepository.save(WatchlistFixture.createWatchlist(user, stock, group));
        watchlistRepository.save(WatchlistFixture.createWatchlist(user, stock2, group));

        // when
        List<Watchlist> result = watchlistRepository.findAllWithStockByUserId(user.getId());

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStock().getStockName()).isEqualTo("삼성전자");
        assertThat(result.get(1).getStock().getStockName()).isEqualTo("SK하이닉스");
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

    @Test
    @DisplayName("[그룹이 없는(미분류) 행만 조회한다]")
    void findAllByUserIdAndGroupIsNull_returnsOnlyUngroupedRows() {
        // given: 레거시 미분류 행을 흉내낸다. Watchlist.of()는 그룹을 필수로
        // 받으므로 정상 생성 경로로는 만들 수 없어, JPQL 벌크 업데이트로 그룹
        // 참조만 직접 비운다(reconcileUngroupedWatchlist가 정리해야 할 과거
        // 데이터 상태를 재현하는 것이 이 테스트의 목적).
        Watchlist grouped = watchlistRepository.save(WatchlistFixture.createWatchlist(user, stock, group));
        Stock stock2 = stockRepository.save(StockFixture.createStock("000660", "SK하이닉스"));
        Watchlist legacyUngrouped = watchlistRepository.save(
            WatchlistFixture.createWatchlist(user, stock2, group));
        entityManager.getEntityManager()
            .createQuery("update Watchlist w set w.group = null where w.id = :id")
            .setParameter("id", legacyUngrouped.getId())
            .executeUpdate();
        entityManager.getEntityManager().clear();

        // when
        List<Watchlist> result = watchlistRepository.findAllByUser_IdAndGroupIsNull(user.getId());

        // then
        assertThat(result).extracting(Watchlist::getId).containsExactly(legacyUngrouped.getId());
        assertThat(result).extracting(Watchlist::getId).doesNotContain(grouped.getId());
    }

    @Test
    @DisplayName("[관심종목 등록자 수가 많은 종목 순으로 코드를 반환한다]")
    void findStockCodesOrderByWatcherCountDesc_ordersByDistinctWatcherCount() {
        // given: stock은 2명(user, otherUser)이, stock2는 1명만 등록
        Stock stock2 = stockRepository.save(StockFixture.createStock("000660", "SK하이닉스"));
        User otherUser = userRepository.save(
            UserFixture.createUser(user.getProvider(), "other-provider-id"));
        WatchlistGroup otherGroup = watchlistGroupRepository.save(
            WatchlistGroupFixture.createWatchlistGroup(otherUser));
        watchlistRepository.save(WatchlistFixture.createWatchlist(user, stock, group));
        watchlistRepository.save(WatchlistFixture.createWatchlist(otherUser, stock, otherGroup));
        watchlistRepository.save(WatchlistFixture.createWatchlist(user, stock2, group));

        // when
        List<String> result = watchlistRepository.findStockCodesOrderByWatcherCountDesc(PageRequest.of(0, 5));

        // then
        assertThat(result).containsExactly(stock.getStockCode(), stock2.getStockCode());
    }

    @Test
    @DisplayName("[findStockCodesByUserId는 해당 사용자의 관심종목 코드만 반환한다]")
    void findStockCodesByUserId_returnsOnlyThatUsersCodes() {
        // given
        Stock stock2 = stockRepository.save(StockFixture.createStock("000660", "SK하이닉스"));
        User otherUser = userRepository.save(
            UserFixture.createUser(user.getProvider(), "other-provider-id"));
        WatchlistGroup otherGroup = watchlistGroupRepository.save(
            WatchlistGroupFixture.createWatchlistGroup(otherUser));
        watchlistRepository.save(WatchlistFixture.createWatchlist(user, stock, group));
        watchlistRepository.save(WatchlistFixture.createWatchlist(otherUser, stock2, otherGroup));

        // when
        List<String> result = watchlistRepository.findStockCodesByUserId(user.getId());

        // then
        assertThat(result).containsExactly(stock.getStockCode());
    }
}
