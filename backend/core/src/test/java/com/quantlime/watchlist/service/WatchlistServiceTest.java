package com.quantlime.watchlist.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.exception.ValidationException;
import com.quantlime.price.service.DailyPriceService;
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
import com.quantlime.watchlist.repository.WatchlistRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    private DailyPriceService dailyPriceService;

    @Mock
    private ScoreService scoreService;

    @Mock
    private TaskExecutor watchlistTaskExecutor;

    @InjectMocks
    private WatchlistService watchlistService;

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
        verify(dailyPriceService).backfillHistoryIfNeeded(stockCode);
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
            .given(dailyPriceService).backfillHistoryIfNeeded(stockCode);

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
        verify(scoreService).recalculateScore(stockCode);
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
            .given(scoreService).recalculateScore(stockCode);

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
}
