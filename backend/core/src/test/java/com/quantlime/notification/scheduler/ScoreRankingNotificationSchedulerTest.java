package com.quantlime.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.quantlime.notification.domain.NotificationType;
import com.quantlime.notification.service.FcmPushService;
import com.quantlime.score.dto.response.ScoreRankingResponse;
import com.quantlime.score.service.ScoreService;
import com.quantlime.subscription.domain.SubscriptionStatus;
import com.quantlime.subscription.repository.SubscriptionRepository;
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
class ScoreRankingNotificationSchedulerTest {

    @Mock
    private ScoreService scoreService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private FcmPushService fcmPushService;

    @InjectMocks
    private ScoreRankingNotificationScheduler scheduler;

    private ScoreRankingResponse score(String name, String grade) {
        return new ScoreRankingResponse("000001", name, "섹터", null, null, null, 70.0, null, null, 90.0,
            grade, false, null, false, null);
    }

    @Test
    @DisplayName("[구독자가 없으면 스코어 조회도 발송도 하지 않는다]")
    void noSubscribers_skipsEverything() {
        given(subscriptionRepository.findAllUserIdsByStatus(SubscriptionStatus.ACTIVE)).willReturn(List.of());

        scheduler.notifyAfterMarketClose();

        verifyNoInteractions(scoreService, fcmPushService);
    }

    @Test
    @DisplayName("[전체 Top5를 구독자 전원에게, 관심종목 Top5를 구독자별로 보내며 등급이 없으면 종목명만 표기한다]")
    void sendsGlobalToAll_andWatchlistPerUser_withGradeFormatting() {
        // given
        given(subscriptionRepository.findAllUserIdsByStatus(SubscriptionStatus.ACTIVE)).willReturn(List.of(1L, 2L));
        given(scoreService.getAllStocksScoreRanking(5, "all"))
            .willReturn(List.of(score("삼성전자", "STRONG_BUY"), score("카카오", null)));
        given(scoreService.getDashboardScores(1L, "all")).willReturn(List.of(score("네이버", "BUY")));
        given(scoreService.getDashboardScores(2L, "all")).willReturn(List.of());

        // when
        scheduler.notifyBeforeMarketOpen();

        // then
        verify(fcmPushService).sendToUsers(List.of(1L, 2L), NotificationType.SCORE_RANKING_GLOBAL,
            "오늘의 스코어 상위 종목", "삼성전자(STRONG_BUY), 카카오", "/");
        verify(fcmPushService).sendToUser(1L, NotificationType.SCORE_RANKING_WATCHLIST,
            "내 관심종목 스코어 상위", "네이버(BUY)", "/");
        verify(fcmPushService, never()).sendToUser(eq(2L), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("[관심종목 랭킹은 상위 5개까지만 알린다]")
    void watchlistRanking_limitedToTopFive() {
        // given
        given(subscriptionRepository.findAllUserIdsByStatus(SubscriptionStatus.ACTIVE)).willReturn(List.of(1L));
        given(scoreService.getAllStocksScoreRanking(5, "all")).willReturn(List.of());
        given(scoreService.getDashboardScores(1L, "all")).willReturn(List.of(
            score("A", "BUY"), score("B", "BUY"), score("C", "BUY"), score("D", "BUY"),
            score("E", "BUY"), score("F", "BUY")));

        // when
        scheduler.notifyAfterMarketClose();

        // then
        verify(fcmPushService).sendToUser(1L, NotificationType.SCORE_RANKING_WATCHLIST,
            "내 관심종목 스코어 상위", "A(BUY), B(BUY), C(BUY), D(BUY), E(BUY)", "/");
    }

    @Test
    @DisplayName("[전체 랭킹이 비어 있으면(정규화 미실행) 전체 알림만 스킵하고 개인 알림은 계속 보낸다]")
    void emptyGlobalRanking_skipsGlobalOnly() {
        // given
        given(subscriptionRepository.findAllUserIdsByStatus(SubscriptionStatus.ACTIVE)).willReturn(List.of(1L));
        given(scoreService.getAllStocksScoreRanking(5, "all")).willReturn(List.of());
        given(scoreService.getDashboardScores(1L, "all")).willReturn(List.of(score("네이버", "BUY")));

        // when
        scheduler.notifyAfterMarketClose();

        // then
        verify(fcmPushService, never()).sendToUsers(any(), any(), anyString(), anyString(), anyString());
        verify(fcmPushService).sendToUser(eq(1L), eq(NotificationType.SCORE_RANKING_WATCHLIST),
            anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("[한 사용자의 개인 알림이 실패해도 다음 사용자에게 계속 보내고 예외는 전파되지 않는다]")
    void perUserFailure_isolated() {
        // given
        given(subscriptionRepository.findAllUserIdsByStatus(SubscriptionStatus.ACTIVE)).willReturn(List.of(1L, 2L));
        given(scoreService.getAllStocksScoreRanking(5, "all")).willReturn(List.of());
        given(scoreService.getDashboardScores(1L, "all")).willThrow(new RuntimeException("db"));
        given(scoreService.getDashboardScores(2L, "all")).willReturn(List.of(score("네이버", "BUY")));

        // when & then
        assertThatCode(() -> scheduler.notifyAfterMarketClose()).doesNotThrowAnyException();
        verify(fcmPushService).sendToUser(eq(2L), eq(NotificationType.SCORE_RANKING_WATCHLIST),
            anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("[구독자 조회 자체가 실패해도 스케줄러 스레드로 예외가 전파되지 않는다]")
    void subscriberQueryFails_doesNotPropagate() {
        lenient().when(subscriptionRepository.findAllUserIdsByStatus(SubscriptionStatus.ACTIVE))
            .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> scheduler.notifyAfterMarketClose()).doesNotThrowAnyException();
        verifyNoInteractions(scoreService, fcmPushService);
    }
}
