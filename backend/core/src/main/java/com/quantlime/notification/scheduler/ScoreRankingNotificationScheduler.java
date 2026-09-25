package com.quantlime.notification.scheduler;

import com.quantlime.common.util.SafeExecutor;
import com.quantlime.notification.domain.NotificationType;
import com.quantlime.notification.service.FcmPushService;
import com.quantlime.score.dto.response.ScoreRankingResponse;
import com.quantlime.score.service.ScoreService;
import com.quantlime.subscription.domain.SubscriptionStatus;
import com.quantlime.subscription.repository.SubscriptionRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 신규로 스코어를 계산하지 않고, 가장 최근 배치(OhlcvCollectorScheduler
 * 20:10 정산)가 이미 저장·캐싱해둔 스코어를 재사용해 알림만 발송한다 -
 * 장마감 후(20:20)와 장전 재발송(08:30) 두 트리거가 완전히 같은 메서드를
 * 공유하는 이유. 스코어는 구독자 전용 기능(ScoreController 참고)이라
 * 알림도 구독중인 사용자에게만 보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreRankingNotificationScheduler {

    private static final int TOP_N = 5;
    private static final String SCOPE_ALL = "all";

    private final ScoreService scoreService;
    private final SubscriptionRepository subscriptionRepository;
    private final FcmPushService fcmPushService;

    @Scheduled(cron = "0 20 20 * * MON-FRI", zone = "Asia/Seoul")
    public void notifyAfterMarketClose() {
        SafeExecutor.runSafely("장마감 후 스코어 랭킹 알림", this::sendScoreRankingNotifications);
    }

    @Scheduled(cron = "0 30 8 * * MON-FRI", zone = "Asia/Seoul")
    public void notifyBeforeMarketOpen() {
        SafeExecutor.runSafely("장전 스코어 랭킹 알림", this::sendScoreRankingNotifications);
    }

    private void sendScoreRankingNotifications() {
        List<Long> subscriberIds = subscriptionRepository.findAllUserIdsByStatus(SubscriptionStatus.ACTIVE);
        if (subscriberIds.isEmpty()) {
            log.info("스코어 랭킹 알림 대상 구독자 없음 - 스킵");
            return;
        }

        sendGlobalRanking(subscriberIds);
        subscriberIds.forEach(userId ->
            SafeExecutor.runSafely("개인 관심종목 스코어 랭킹 알림(userId=" + userId + ")",
                () -> sendWatchlistRanking(userId)));
    }

    private void sendGlobalRanking(List<Long> subscriberIds) {
        List<ScoreRankingResponse> ranking = scoreService.getAllStocksScoreRanking(TOP_N, SCOPE_ALL);
        if (ranking.isEmpty()) {
            // 전체 랭킹은 횡단면 백분위(compositePercentile)가 채워진 종목만
            // 대상이라, 정규화 단계가 안 돌았으면 비어 있다 - 조용히 넘기면
            // 알림이 왜 안 왔는지 추적할 수 없다.
            log.warn("전체 스코어 랭킹이 비어 있어 전체 Top N 알림 스킵(횡단면 정규화 미실행 여부 확인 필요)");
            return;
        }
        fcmPushService.sendToUsers(subscriberIds, NotificationType.SCORE_RANKING_GLOBAL,
            "오늘의 스코어 상위 종목", formatRanking(ranking), "/");
    }

    private void sendWatchlistRanking(Long userId) {
        List<ScoreRankingResponse> ranking = scoreService.getDashboardScores(userId, SCOPE_ALL).stream()
            .limit(TOP_N)
            .toList();
        if (ranking.isEmpty()) {
            return;
        }
        fcmPushService.sendToUser(userId, NotificationType.SCORE_RANKING_WATCHLIST,
            "내 관심종목 스코어 상위", formatRanking(ranking), "/");
    }

    // "종목명(등급)" 형식 - 백분위(compositePercentile)는 관심종목 등록 직후
    // 단건 계산분처럼 다음 정규화 배치 전까지 null일 수 있어 쓰지 않는다.
    // 등급은 절대점수 기준이라 계산 즉시 채워지고, 데이터 부족 시에만 null.
    private String formatRanking(List<ScoreRankingResponse> ranking) {
        return ranking.stream()
            .map(score -> score.grade() != null
                ? score.stockName() + "(" + score.grade() + ")"
                : score.stockName())
            .collect(Collectors.joining(", "));
    }
}
