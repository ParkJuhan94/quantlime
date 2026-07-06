package com.quantlab.watchlist.service;

import com.quantlab.common.exception.NotFoundException;
import com.quantlab.common.exception.ValidationException;
import com.quantlab.price.service.DailyPriceService;
import com.quantlab.score.service.ScoreService;
import com.quantlab.stock.StockFixture;
import com.quantlab.stock.domain.Stock;
import com.quantlab.stock.service.StockMasterService;
import com.quantlab.user.UserFixture;
import com.quantlab.user.domain.User;
import com.quantlab.user.service.UserService;
import com.quantlab.watchlist.domain.Watchlist;
import com.quantlab.watchlist.repository.WatchlistRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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
    private DailyPriceService dailyPriceService;

    @Mock
    private ScoreService scoreService;

    @InjectMocks
    private WatchlistService watchlistService;

    private final User user = UserFixture.createUser();
    private final Stock stock = StockFixture.createStock();

    @Test
    @DisplayName("[관심 종목이 없으면 신규 등록한다]")
    void addWatchlist_notRegistered_saves() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        given(userService.getById(userId)).willReturn(user);
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(false);
        given(watchlistRepository.save(org.mockito.ArgumentMatchers.any(Watchlist.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        // when
        Watchlist result = watchlistService.addWatchlist(userId, stockCode);

        // then
        assertThat(result.getStock()).isEqualTo(stock);
        verify(watchlistRepository).save(org.mockito.ArgumentMatchers.any(Watchlist.class));
    }

    @Test
    @DisplayName("[이미 등록된 관심 종목이면 예외가 발생한다]")
    void addWatchlist_alreadyExists_throwsValidationException() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        given(userService.getById(userId)).willReturn(user);
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(true);

        // when & then
        assertThatThrownBy(() -> watchlistService.addWatchlist(userId, stockCode))
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
        given(watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(false);
        given(watchlistRepository.save(org.mockito.ArgumentMatchers.any(Watchlist.class)))
            .willThrow(new DataIntegrityViolationException("duplicate"));

        // when & then
        assertThatThrownBy(() -> watchlistService.addWatchlist(userId, stockCode))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("[관심 종목 등록 시 이력 백필을 트리거한다]")
    void addWatchlist_success_triggersHistoryBackfill() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        given(userService.getById(userId)).willReturn(user);
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(false);
        given(watchlistRepository.save(org.mockito.ArgumentMatchers.any(Watchlist.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        // when
        watchlistService.addWatchlist(userId, stockCode);

        // then
        verify(dailyPriceService).backfillHistoryIfNeeded(stockCode);
    }

    @Test
    @DisplayName("[이력 백필이 실패해도 관심 종목 등록 자체는 성공한다]")
    void addWatchlist_backfillFails_registrationStillSucceeds() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        given(userService.getById(userId)).willReturn(user);
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(false);
        given(watchlistRepository.save(org.mockito.ArgumentMatchers.any(Watchlist.class)))
            .willAnswer(invocation -> invocation.getArgument(0));
        willThrow(new RuntimeException("토스 API 장애"))
            .given(dailyPriceService).backfillHistoryIfNeeded(stockCode);

        // when
        Watchlist result = watchlistService.addWatchlist(userId, stockCode);

        // then
        assertThat(result.getStock()).isEqualTo(stock);
    }

    @Test
    @DisplayName("[관심 종목 등록 시 스코어 재계산을 트리거한다]")
    void addWatchlist_success_triggersScoreRecalculation() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        given(userService.getById(userId)).willReturn(user);
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(false);
        given(watchlistRepository.save(org.mockito.ArgumentMatchers.any(Watchlist.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        // when
        watchlistService.addWatchlist(userId, stockCode);

        // then
        verify(scoreService).recalculateScore(stockCode);
    }

    @Test
    @DisplayName("[스코어 재계산이 실패해도 관심 종목 등록 자체는 성공한다]")
    void addWatchlist_scoreRecalculationFails_registrationStillSucceeds() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        given(userService.getById(userId)).willReturn(user);
        given(stockMasterService.getStockByCode(stockCode)).willReturn(stock);
        given(watchlistRepository.existsByUser_IdAndStock_StockCode(userId, stockCode))
            .willReturn(false);
        given(watchlistRepository.save(org.mockito.ArgumentMatchers.any(Watchlist.class)))
            .willAnswer(invocation -> invocation.getArgument(0));
        willThrow(new RuntimeException("퀀트 엔진 장애"))
            .given(scoreService).recalculateScore(stockCode);

        // when
        Watchlist result = watchlistService.addWatchlist(userId, stockCode);

        // then
        assertThat(result.getStock()).isEqualTo(stock);
    }

    @Test
    @DisplayName("[등록된 관심 종목을 해제한다]")
    void removeWatchlist_registered_deletes() {
        // given
        Long userId = 1L;
        String stockCode = stock.getStockCode();
        Watchlist watchlist = Watchlist.of(user, stock);
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
}
