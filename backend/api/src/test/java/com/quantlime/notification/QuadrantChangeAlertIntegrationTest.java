package com.quantlime.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.quantlime.notification.domain.Notification;
import com.quantlime.notification.domain.NotificationType;
import com.quantlime.notification.repository.NotificationRepository;
import com.quantlime.notification.service.QuadrantChangeAlertService;
import com.quantlime.score.domain.Quadrant;
import com.quantlime.score.domain.Score;
import com.quantlime.score.repository.ScoreRepository;
import com.quantlime.stock.StockFixture;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.repository.StockRepository;
import com.quantlime.support.ApiTestSupport;
import com.quantlime.user.UserFixture;
import com.quantlime.user.domain.OAuthProvider;
import com.quantlime.user.domain.User;
import com.quantlime.user.repository.UserRepository;
import com.quantlime.watchlist.domain.Watchlist;
import com.quantlime.watchlist.domain.WatchlistGroup;
import com.quantlime.watchlist.repository.WatchlistGroupRepository;
import com.quantlime.watchlist.repository.WatchlistRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 실제 MySQL/Redis로 사분면 변화 알림의 쿼리(알림 켠 그룹 종목 추출, 최신/직전 스코어 조회)와
 * 중복 발송 방지 장부를 끝까지 검증한다 - 단위 테스트는 이 쿼리들을 목으로 가려서 못 본다.
 */
@Tag("integration")
class QuadrantChangeAlertIntegrationTest extends ApiTestSupport {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

    @Autowired
    private QuadrantChangeAlertService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private ScoreRepository scoreRepository;
    @Autowired
    private WatchlistGroupRepository watchlistGroupRepository;
    @Autowired
    private WatchlistRepository watchlistRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private User user;
    private Stock samsung;
    private Stock hynix;
    private WatchlistGroup alertGroup;
    private WatchlistGroup quietGroup;

    @BeforeEach
    void setUp() {
        // DatabaseCleaner는 MySQL만 비우고 user id가 매번 1부터 다시 시작하므로,
        // 이전 테스트가 남긴 중복 방지 장부(Redis)를 직접 지운다.
        redisTemplate.delete(redisTemplate.keys("notification:quadrant:sent:*"));
        user = userRepository.save(UserFixture.createUser(OAuthProvider.GOOGLE, "qa-user"));
        samsung = stockRepository.save(StockFixture.createStock("005930", "삼성전자"));
        hynix = stockRepository.save(StockFixture.createStock("000660", "SK하이닉스"));
        alertGroup = watchlistGroupRepository.save(WatchlistGroup.of(user, "알림 그룹", 0));
        alertGroup.changeQuadrantAlert(true);
        watchlistGroupRepository.save(alertGroup);
        quietGroup = watchlistGroupRepository.save(WatchlistGroup.of(user, "조용한 그룹", 1));
    }

    private void saveScore(String code, LocalDate date, Quadrant quadrant) {
        scoreRepository.save(Score.of(code, date, 50.0, 50.0, 50.0, null, quadrant, null, false));
    }

    @Test
    @DisplayName("[알림을 켠 그룹의 종목만 대상이고, 사분면이 바뀐 종목은 인앱 알림 한 건으로 저장된다]")
    void notifiesOnlyOptedInGroupStocks() {
        watchlistRepository.save(Watchlist.of(user, samsung, alertGroup, 0));
        watchlistRepository.save(Watchlist.of(user, hynix, quietGroup, 1));
        saveScore("005930", TODAY.minusDays(1), Quadrant.TREND_UP_OVERSOLD);
        saveScore("005930", TODAY, Quadrant.TREND_UP_OVERBOUGHT);
        saveScore("000660", TODAY.minusDays(1), Quadrant.TREND_UP_OVERSOLD);
        saveScore("000660", TODAY, Quadrant.TREND_DOWN_OVERSOLD);

        int count = service.notifyUser(user.getId(), TODAY);

        assertThat(count).isEqualTo(1);
        List<Notification> saved = notificationRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getType()).isEqualTo(NotificationType.QUADRANT_CHANGE);
        assertThat(saved.get(0).getContent()).contains("삼성전자").doesNotContain("SK하이닉스");
    }

    @Test
    @DisplayName("[같은 날 판정을 다시 돌려도 이미 통지한 변화는 다시 저장되지 않는다]")
    void rerun_isIdempotent() {
        watchlistRepository.save(Watchlist.of(user, samsung, alertGroup, 0));
        saveScore("005930", TODAY.minusDays(1), Quadrant.TREND_UP_OVERSOLD);
        saveScore("005930", TODAY, Quadrant.TREND_UP_OVERBOUGHT);

        assertThat(service.notifyUser(user.getId(), TODAY)).isEqualTo(1);
        assertThat(service.notifyUser(user.getId(), TODAY)).isZero();

        assertThat(notificationRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("[사분면이 그대로면 알림이 없다]")
    void unchangedQuadrant_noNotification() {
        watchlistRepository.save(Watchlist.of(user, samsung, alertGroup, 0));
        saveScore("005930", TODAY.minusDays(1), Quadrant.TREND_UP_OVERSOLD);
        saveScore("005930", TODAY, Quadrant.TREND_UP_OVERSOLD);

        assertThat(service.notifyUser(user.getId(), TODAY)).isZero();

        assertThat(notificationRepository.findAll()).isEmpty();
    }
}
