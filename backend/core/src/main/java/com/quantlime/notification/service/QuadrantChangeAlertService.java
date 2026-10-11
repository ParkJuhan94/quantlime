package com.quantlime.notification.service;

import com.quantlime.notification.cache.QuadrantAlertSentStore;
import com.quantlime.notification.domain.NotificationType;
import com.quantlime.score.domain.Quadrant;
import com.quantlime.score.domain.Score;
import com.quantlime.score.implement.ScoreReader;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.implement.StockReader;
import com.quantlime.watchlist.implement.WatchlistReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 사용자가 알림을 켠 관심 그룹의 종목 중 사분면(추세/평균회귀 4분류)이 직전 스코어 대비
 * 바뀐 것을 한 건의 요약 알림으로 보낸다. 스코어 배치가 끝난 뒤 사용자당 한 번 호출된다.
 *
 * <p>종목마다 "최신 스코어 행"과 "그 직전 행"의 사분면을 비교한다 - 국내는 당일, 해외는
 * 전 미국 거래일처럼 종목군마다 최신 산출일이 달라서 날짜를 하나로 가정하지 않는다.
 * 같은 변화를 두 번 통지하지 않도록 (종목, 산출일)을 {@link QuadrantAlertSentStore}에
 * 기록하고, 산출일이 {@value #MAX_STALENESS_DAYS}일보다 오래된 행은 판정에서 뺀다(주말·
 * 연휴는 감안하되, 오래전 종목의 묵은 변화가 장부가 비었을 때 되살아나지 않게 함).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuadrantChangeAlertService {

    private static final int MAX_STALENESS_DAYS = 4;
    private static final int NAMES_IN_CONTENT = 3;
    private static final String LINK_URL = "/";

    private final WatchlistReader watchlistReader;
    private final ScoreReader scoreReader;
    private final StockReader stockReader;
    private final FcmPushService fcmPushService;
    private final QuadrantAlertSentStore sentStore;

    /** @return 통지한 변화 건수(알림을 보내지 않았으면 0) */
    public int notifyUser(Long userId) {
        return notifyUser(userId, LocalDate.now());
    }

    public int notifyUser(Long userId, LocalDate today) {
        List<String> stockCodes = watchlistReader.findStockCodesInQuadrantAlertGroups(userId);
        if (stockCodes.isEmpty()) {
            return 0;
        }

        List<QuadrantChange> changes = findChanges(stockCodes, today).stream()
            .filter(change -> sentStore.markIfNew(userId, change.stockCode(), change.scoreDate()))
            .toList();
        if (changes.isEmpty()) {
            return 0;
        }

        try {
            fcmPushService.sendToUser(userId, NotificationType.QUADRANT_CHANGE,
                "관심종목 사분면 변화 " + changes.size() + "건", formatContent(changes), LINK_URL);
        } catch (RuntimeException e) {
            // 재시도(Kafka)에서 다시 통지되도록 장부를 되돌린다.
            changes.forEach(change -> sentStore.unmark(userId, change.stockCode(), change.scoreDate()));
            throw e;
        }
        log.info("사분면 변화 알림 발송: userId={}, 건수={}", userId, changes.size());
        return changes.size();
    }

    private List<QuadrantChange> findChanges(List<String> stockCodes, LocalDate today) {
        LocalDate oldestAllowed = today.minusDays(MAX_STALENESS_DAYS);
        Map<LocalDate, List<Score>> latestByDate = scoreReader.findLatestScoresOnOrBefore(stockCodes, today)
            .stream()
            .filter(score -> !score.getScoreDate().isBefore(oldestAllowed))
            .filter(score -> score.getQuadrant() != null)
            .collect(Collectors.groupingBy(Score::getScoreDate));

        List<QuadrantChange> changes = new ArrayList<>();
        latestByDate.forEach((scoreDate, latestScores) -> {
            List<String> codes = latestScores.stream().map(Score::getStockCode).toList();
            Map<String, Score> previousByCode = scoreReader.findLatestScoresOnOrBefore(codes, scoreDate.minusDays(1))
                .stream()
                .collect(Collectors.toMap(Score::getStockCode, Function.identity(), (a, b) -> a));
            for (Score latest : latestScores) {
                Score previous = previousByCode.get(latest.getStockCode());
                if (previous != null && previous.getQuadrant() != null
                    && previous.getQuadrant() != latest.getQuadrant()) {
                    changes.add(new QuadrantChange(latest.getStockCode(), scoreDate,
                        previous.getQuadrant(), latest.getQuadrant()));
                }
            }
        });
        return changes;
    }

    private String formatContent(List<QuadrantChange> changes) {
        Map<String, String> nameByCode = stockReader
            .findByStockCodeIn(changes.stream().map(QuadrantChange::stockCode).toList()).stream()
            .collect(Collectors.toMap(Stock::getStockCode, Stock::getDisplayName, (a, b) -> a));

        String head = changes.stream()
            .limit(NAMES_IN_CONTENT)
            .map(change -> nameByCode.getOrDefault(change.stockCode(), change.stockCode())
                + " " + change.from().getLabel() + "→" + change.to().getLabel())
            .collect(Collectors.joining(", "));
        int rest = changes.size() - NAMES_IN_CONTENT;
        return rest > 0 ? head + " 외 " + rest + "건" : head;
    }

    private record QuadrantChange(String stockCode, LocalDate scoreDate, Quadrant from, Quadrant to) {
    }
}
