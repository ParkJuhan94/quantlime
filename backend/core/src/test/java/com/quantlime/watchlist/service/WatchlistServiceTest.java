package com.quantlime.watchlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.price.service.DomesticDailyPriceService;
import com.quantlime.score.service.ScoreService;
import com.quantlime.stock.StockFixture;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.service.StockMasterService;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.User;
import com.quantlime.user.service.UserService;
import com.quantlime.watchlist.WatchlistFixture;
import com.quantlime.watchlist.WatchlistGroupFixture;
import com.quantlime.watchlist.domain.Watchlist;
import com.quantlime.watchlist.domain.WatchlistGroup;
import com.quantlime.watchlist.implement.WatchlistAppender;
import com.quantlime.watchlist.implement.WatchlistReader;
import com.quantlime.watchlist.repository.WatchlistRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private StockMasterService stockMasterService;

    @Mock
    private WatchlistRepository watchlistRepository;

    @Mock
    private WatchlistGroupService watchlistGroupService;

    @Mock
    private DomesticDailyPriceService domesticDailyPriceService;

    @Mock
    private ScoreService scoreService;

    @Mock
    private TaskExecutor watchlistTaskExecutor;

    private WatchlistService watchlistService;

    @BeforeEach
    void setUp() {
        watchlistService = new WatchlistService(userService, stockMasterService,
            new WatchlistReader(watchlistRepository), new WatchlistAppender(watchlistRepository),
            watchlistGroupService, domesticDailyPriceService, scoreService, watchlistTaskExecutor);
    }

    private final User user = UserFixture.createUser();
    private final Stock stock = StockFixture.createStock();
    private final WatchlistGroup group = WatchlistGroupFixture.createWatchlistGroup(user);
    private final Long groupId = 10L;

    // 등록 후속작업(백필/스코어 재계산)은 별도 스레드로 넘겨 실행되므로, 그
    // 결과를 검증해야 하는 테스트에서는 실행기가 즉시 동기 실행하도록 스텁한다.
    private void runPostRegistrationTasksSynchronously() {
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(watchlistTaskExecutor).execute(any());
    }

    @Test
    @DisplayName("[관심 종목이 없으면 신규 등록한다]")
    void addWatchlist_notRegistered_saves() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        given(userService.getById(userId)).willReturn(user);
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(watchlistGroupService.getOwnedGroup(userId, groupId)).willReturn(group);
        given(watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(false);
        given(watchlistRepository.save(org.mockito.ArgumentMatchers.any(Watchlist.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        // when
        Watchlist result = watchlistService.addWatchlist(userId, stockCode, groupId);

        // then
        assertThat(result.getStock()).isEqualTo(stock);
        assertThat(result.getGroup()).isEqualTo(group);
        verify(watchlistRepository).save(org.mockito.ArgumentMatchers.any(Watchlist.class));
    }

    @Test
    @DisplayName("[존재하지 않거나 소유하지 않은 그룹으로 등록하면 예외가 발생한다]")
    void addWatchlist_groupNotOwned_throwsNotFoundException() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        given(userService.getById(userId)).willReturn(user);
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(watchlistGroupService.getOwnedGroup(userId, groupId))
            .willThrow(new NotFoundException(com.quantlime.watchlist.exception.WatchlistErrorCode.NOT_FOUND_WATCHLIST_GROUP));

        // when & then
        assertThatThrownBy(() -> watchlistService.addWatchlist(userId, stockCode, groupId))
            .isInstanceOf(NotFoundException.class);
        verify(watchlistRepository, never()).save(any(Watchlist.class));
    }

    @Test
    @DisplayName("[이미 등록된 관심 종목이면 예외가 발생한다]")
    void addWatchlist_alreadyExists_throwsValidationException() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        given(userService.getById(userId)).willReturn(user);
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(watchlistGroupService.getOwnedGroup(userId, groupId)).willReturn(group);
        given(watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(true);

        // when & then
        assertThatThrownBy(() -> watchlistService.addWatchlist(userId, stockCode, groupId))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("[동시 등록 경쟁으로 유니크 제약 위반이 발생해도 검증 예외로 변환한다]")
    void addWatchlist_raceCondition_throwsValidationException() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        given(userService.getById(userId)).willReturn(user);
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(watchlistGroupService.getOwnedGroup(userId, groupId)).willReturn(group);
        given(watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(false);
        given(watchlistRepository.save(org.mockito.ArgumentMatchers.any(Watchlist.class)))
            .willThrow(new DataIntegrityViolationException("duplicate"));

        // when & then
        assertThatThrownBy(() -> watchlistService.addWatchlist(userId, stockCode, groupId))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("[관심 종목 등록 시 이력 백필을 트리거한다]")
    void addWatchlist_success_triggersHistoryBackfill() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        runPostRegistrationTasksSynchronously();
        given(userService.getById(userId)).willReturn(user);
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(watchlistGroupService.getOwnedGroup(userId, groupId)).willReturn(group);
        given(watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(false);
        given(watchlistRepository.save(org.mockito.ArgumentMatchers.any(Watchlist.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        // when
        watchlistService.addWatchlist(userId, stockCode, groupId);

        // then
        verify(domesticDailyPriceService).backfillHistoryIfNeeded(stockCode);
    }

    @Test
    @DisplayName("[이력 백필이 실패해도 관심 종목 등록 자체는 성공한다]")
    void addWatchlist_backfillFails_registrationStillSucceeds() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        runPostRegistrationTasksSynchronously();
        given(userService.getById(userId)).willReturn(user);
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(watchlistGroupService.getOwnedGroup(userId, groupId)).willReturn(group);
        given(watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(false);
        given(watchlistRepository.save(org.mockito.ArgumentMatchers.any(Watchlist.class)))
            .willAnswer(invocation -> invocation.getArgument(0));
        willThrow(new RuntimeException("토스 API 장애"))
            .given(domesticDailyPriceService).backfillHistoryIfNeeded(stockCode);

        // when
        Watchlist result = watchlistService.addWatchlist(userId, stockCode, groupId);

        // then
        assertThat(result.getStock()).isEqualTo(stock);
    }

    @Test
    @DisplayName("[관심 종목 등록 시 스코어 재계산을 트리거한다]")
    void addWatchlist_success_triggersScoreRecalculation() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        runPostRegistrationTasksSynchronously();
        given(userService.getById(userId)).willReturn(user);
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(watchlistGroupService.getOwnedGroup(userId, groupId)).willReturn(group);
        given(watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(false);
        given(watchlistRepository.save(org.mockito.ArgumentMatchers.any(Watchlist.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        // when
        watchlistService.addWatchlist(userId, stockCode, groupId);

        // then
        verify(scoreService).recalculateDomesticScore(stockCode);
    }

    @Test
    @DisplayName("[스코어 재계산이 실패해도 관심 종목 등록 자체는 성공한다]")
    void addWatchlist_scoreRecalculationFails_registrationStillSucceeds() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        runPostRegistrationTasksSynchronously();
        given(userService.getById(userId)).willReturn(user);
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(watchlistGroupService.getOwnedGroup(userId, groupId)).willReturn(group);
        given(watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(false);
        given(watchlistRepository.save(org.mockito.ArgumentMatchers.any(Watchlist.class)))
            .willAnswer(invocation -> invocation.getArgument(0));
        willThrow(new RuntimeException("퀀트 엔진 장애"))
            .given(scoreService).recalculateDomesticScore(stockCode);

        // when
        Watchlist result = watchlistService.addWatchlist(userId, stockCode, groupId);

        // then
        assertThat(result.getStock()).isEqualTo(stock);
    }

    @Test
    @DisplayName("[등록된 관심 종목을 해제한다]")
    void removeWatchlist_registered_deletes() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        Watchlist watchlist = WatchlistFixture.createWatchlist(user, stock, group);
        given(watchlistRepository.findByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(Optional.of(watchlist));

        // when
        watchlistService.removeWatchlist(userId, stockCode);

        // then
        verify(watchlistRepository).delete(watchlist);
    }

    @Test
    @DisplayName("[등록되지 않은 관심 종목을 해제하면 예외가 발생한다]")
    void removeWatchlist_notRegistered_throwsNotFoundException() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        given(watchlistRepository.findByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> watchlistService.removeWatchlist(userId, stockCode))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("[관심 종목을 다른 그룹으로 이동한다]")
    void moveToGroup_ownedGroup_reassignsGroup() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        Watchlist watchlist = WatchlistFixture.createWatchlist(user, stock, group);
        WatchlistGroup targetGroup = WatchlistGroupFixture.createWatchlistGroup(user);
        given(watchlistRepository.findByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(Optional.of(watchlist));
        given(watchlistGroupService.getOwnedGroup(userId, groupId)).willReturn(targetGroup);

        // when
        watchlistService.moveToGroup(userId, stockCode, groupId);

        // then
        assertThat(watchlist.getGroup()).isEqualTo(targetGroup);
    }

    @Test
    @DisplayName("[등록되지 않은 관심 종목을 이동시키려 하면 예외가 발생한다]")
    void moveToGroup_notRegistered_throwsNotFoundException() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        given(watchlistRepository.findByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> watchlistService.moveToGroup(userId, stockCode, groupId))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("[미분류 행이 없으면 추가 조회 없이 목록을 그대로 반환한다]")
    void getWatchlist_noUngroupedItems_skipsReconciliation() {
        // given
        Long userId = 1L;
        given(watchlistRepository.findAllByUser_IdAndGroupIsNull(userId)).willReturn(List.of());
        given(watchlistRepository.findAllWithStockByUserId(userId)).willReturn(List.of());

        // when
        watchlistService.getWatchlist(userId);

        // then
        verify(watchlistGroupService, never()).findOrCreateDefaultGroup(userId);
    }

    @Test
    @DisplayName("[미분류 행이 있으면 기본 그룹으로 자동 이동시킨 뒤 조회한다]")
    void getWatchlist_hasUngroupedItems_reassignsToDefaultGroupThenReturns() {
        // given: 그룹 도입 이전에 등록된 레거시 행을 흉내낸다
        Long userId = 1L;
        Watchlist legacyUngrouped = WatchlistFixture.createWatchlist(user, stock, group);
        given(watchlistRepository.findAllByUser_IdAndGroupIsNull(userId)).willReturn(List.of(legacyUngrouped));
        given(watchlistGroupService.findOrCreateDefaultGroup(userId)).willReturn(group);
        given(watchlistRepository.findAllWithStockByUserId(userId)).willReturn(List.of(legacyUngrouped));

        // when
        watchlistService.getWatchlist(userId);

        // then
        verify(watchlistGroupService).findOrCreateDefaultGroup(userId);
        assertThat(legacyUngrouped.getGroup()).isEqualTo(group);
    }

    private Watchlist itemWithId(Long id, int sortOrder) {
        Watchlist item = Watchlist.of(user, stock, group, sortOrder);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    @Test
    @DisplayName("[재배열은 보낸 id 순서대로 sortOrder를 0부터 다시 매긴다]")
    void reorderWatchlist_assignsSortOrderInRequestedOrder() {
        // given
        Watchlist first = itemWithId(1L, 0);
        Watchlist second = itemWithId(2L, 1);
        Watchlist third = itemWithId(3L, 2);
        List<Long> requested = List.of(3L, 1L, 2L);
        given(watchlistRepository.findAllByUser_IdAndIdIn(1L, requested)).willReturn(List.of(first, second, third));

        // when
        watchlistService.reorderWatchlist(1L, requested);

        // then
        assertThat(third.getSortOrder()).isZero();
        assertThat(first.getSortOrder()).isEqualTo(1);
        assertThat(second.getSortOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("[재배열 - 내 소유가 아니라 조회되지 않은 id는 건너뛰고 나머지 순서는 유지한다]")
    void reorderWatchlist_unknownIdIsSkipped() {
        // given: 99L은 다른 사용자의 항목이라 조회 결과에 없다
        Watchlist mine = itemWithId(1L, 5);
        List<Long> requested = List.of(99L, 1L);
        given(watchlistRepository.findAllByUser_IdAndIdIn(1L, requested)).willReturn(List.of(mine));

        // when
        watchlistService.reorderWatchlist(1L, requested);

        // then: 요청 목록상 위치(1)가 그대로 반영된다
        assertThat(mine.getSortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("[인기 종목은 등록자 수 순 코드 목록을 순서 그대로 종목으로 바꿔 돌려준다]")
    void getPopularStocks_keepsRankingOrder() {
        // given
        List<String> rankedCodes = List.of("005930", "000660");
        given(watchlistRepository.findStockCodesOrderByWatcherCountDesc(PageRequest.of(0, 2))).willReturn(rankedCodes);
        given(stockMasterService.getStocksByCodesInOrder(rankedCodes)).willReturn(List.of(stock));

        // when & then
        assertThat(watchlistService.getPopularStocks(2)).containsExactly(stock);
    }

    @Test
    @DisplayName("[관심종목 코드 집합은 중복 없이 Set으로 돌려준다]")
    void getWatchlistStockCodes_returnsDistinctSet() {
        given(watchlistRepository.findStockCodesByUserId(1L)).willReturn(List.of("005930", "000660"));

        assertThat(watchlistService.getWatchlistStockCodes(1L)).containsExactlyInAnyOrder("005930", "000660");
    }
}
