package com.quantlime.telegramfeed.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.common.lock.RedisLockService;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.telegramfeed.domain.TelegramDigest;
import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostStatus;
import com.quantlime.telegramfeed.dto.TelegramDigestGenerateResult;
import com.quantlime.telegramfeed.event.TelegramDigestGenerationRequestedEvent;
import com.quantlime.telegramfeed.exception.TelegramFeedErrorCode;
import com.quantlime.telegramfeed.implement.TelegramDigestAppender;
import com.quantlime.telegramfeed.implement.TelegramDigestGenerator;
import com.quantlime.telegramfeed.implement.TelegramDigestReader;
import com.quantlime.telegramfeed.implement.TelegramPostReader;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.implement.ChannelReader;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 채널×오늘 단위로 그날 SELECTED된 텔레그램 글 전부를 하나로 합쳐 AI
 * 다이제스트를 생성한다(2026-08-15, 글 단위 SummaryCollectionFacade 대응물을
 * 대체). TelegramCollectionScheduler(수집, 1시간마다)와 스케줄을 분리해
 * 이 파사드는 하루 3회(08:30/13:30/20:30)만 실행된다 - 수집을 촘촘히 돌려도
 * 다이제스트 재생성(Gemini 호출)까지 그 빈도를 따라가면 유튜브와 공유하는
 * 무료 티어 일일 쿼터를 초과하기 때문(docs/ROADMAP.md "Phase 8 P7" 참고).
 *
 * <p><b>2026-09-30 카프카 다도메인 확장 Phase 4</b> - 트리거 빈도(하루
 * 3회)는 위 쿼터 제약 때문에 그대로 유지하고, 그 대신 "채널마다 순차 루프를
 * 돌며 예외를 삼켜 장애를 격리하던" 방식을 "채널마다 이벤트를 발행해 Kafka
 * 재시도(30s→90s→270s)+DLT로 격리"하는 방식으로 바꿨다 - videofeed/market/
 * subscription/payment와 동일한 원칙(SummaryCollectionFacade.publishBacklog가
 * 가장 가까운 선례). {@link #publishAll()}은 더 이상 실제 생성을 하지 않고
 * 이벤트만 발행하므로, 채널 단위 실패 격리 테스트는 이제 여기가 아니라
 * {@link #generateForChannel(Long, LocalDate)}(Kafka 컨슈머가 호출)와 그
 * 컨슈머의 재시도/DLT 처리 쪽 책임이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramDigestGenerationFacade {

    private static final String LOCK_KEY = "lock:telegram-digest-generate";
    // 이벤트 발행만 하므로 실제 생성(Gemini 호출)이 걸리던 예전보다 훨씬
    // 빨리 끝난다 - TTL을 30분에서 5분으로 줄여 락이 불필요하게 오래 남지
    // 않게 한다.
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);
    // TelegramDigestGenerationScheduler cron(08:30/13:30/20:30)의 마지막 회차 - cron을 바꾸면 같이 바꿀 것.
    static final LocalTime LAST_DAILY_RUN = LocalTime.of(20, 30);

    private final RedisLockService redisLockService;
    private final ChannelReader channelReader;
    private final TelegramPostReader telegramPostReader;
    private final TelegramDigestGenerator telegramDigestGenerator;
    private final TelegramDigestAppender telegramDigestAppender;
    private final TelegramDigestReader telegramDigestReader;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public Optional<Integer> runAllExclusively() {
        return redisLockService.runExclusively(LOCK_KEY, LOCK_TTL, this::publishAll);
    }

    // 락 없이 발행 로직만 실행 - 테스트에서 직접 호출. 스케줄러/관리자 엔드포인트는
    // 반드시 runAllExclusively()를 통해서만 호출할 것.
    public int publishAll() {
        List<Channel> channels = channelReader.findActive(Platform.TELEGRAM);
        LocalDate today = LocalDate.now(clock);
        channels.forEach(channel ->
            eventPublisher.publishEvent(new TelegramDigestGenerationRequestedEvent(channel.getId(), today)));
        LocalDate yesterday = today.minusDays(1);
        List<Channel> missed = channels.stream().filter(channel -> missedLastRun(channel, yesterday)).toList();
        missed.forEach(channel ->
            eventPublisher.publishEvent(new TelegramDigestGenerationRequestedEvent(channel.getId(), yesterday)));
        log.info("텔레그램 다이제스트 생성 이벤트 발행: {}건, 전날 마지막 회차 누락분 재생성: {}건", channels.size(), missed.size());
        return channels.size();
    }

    /**
     * 그날 마지막 회차(20:30)가 실패했는지(2026-10-31). 실패하면 그날 다이제스트가 13:30 버전에 멈추는데, 자정이 지나면
     * {@link #publishAll()}이 오늘 날짜만 다루므로 영영 갱신되지 않았다. "마지막 회차 전에 수집된 SELECTED 글 중
     * 다이제스트 갱신 시각 이후의 글"이 있을 때만 true라, 20:30이 정상이었던 날은 Gemini 호출이 늘지 않는다.
     * 20:30 이후에 수집된 글은 원래부터 그날 다이제스트에 들어가지 않으므로 기준에서 뺀다.
     */
    private boolean missedLastRun(Channel channel, LocalDate date) {
        LocalDateTime lastRunAt = date.atTime(LAST_DAILY_RUN);
        Optional<LocalDateTime> digestUpdatedAt = telegramDigestReader.findByChannelAndDigestDate(channel, date)
            .map(TelegramDigest::getUpdatedAt);
        if (digestUpdatedAt.isPresent() && !digestUpdatedAt.get().isBefore(lastRunAt)) {
            return false;
        }
        LocalDateTime dayStart = date.atStartOfDay();
        return telegramPostReader.existsCollectedBetween(channel, TelegramPostStatus.SELECTED,
            dayStart, date.plusDays(1).atStartOfDay(), digestUpdatedAt.orElse(dayStart), lastRunAt);
    }

    /**
     * {@code TelegramDigestGenerationConsumer}(event 모듈)가 채널별로
     * 호출한다. "thin event, thick lookup" 원칙 - 이벤트는 channelId+date만
     * 싣고, 여기서 채널을 다시 조회해 최신 상태로 처리한다. 실패를 여기서
     * 삼키지 않고 그대로 던진다 - Kafka {@code @RetryableTopic}이 재시도를,
     * 소진되면 DLT 핸들러가 이관을 맡는다(예전 동기 루프처럼 이 메서드
     * 자신이 실패를 흡수하면 재시도/DLT가 아예 발동하지 않는다).
     */
    public TelegramDigestGenerateResult generateForChannel(Long channelId, LocalDate date) {
        Channel channel = channelReader.findById(channelId)
            .orElseThrow(() -> new NotFoundException(TelegramFeedErrorCode.NOT_FOUND_CHANNEL));
        TelegramDigestGenerateResult result = generateForChannel(channel, date);
        // 예전엔 스케줄러의 logSummary가 채널 전체를 모아 한 번에 로그를
        // 남겼지만, 이벤트화로 채널별 호출이 분리되며 그 지점이 사라졌다 -
        // 관측성 유지를 위해 여기서 채널 단위로 남긴다(ScoreService/
        // DomesticDailyPriceService 등 다른 per-unit 처리 서비스의 완료
        // 로그 관례와 동일).
        if (result.reason() == null) {
            log.info("텔레그램 다이제스트 생성 완료: channel={}, date={}, sourcePostCount={}",
                channel.getName(), date, result.sourcePostCount());
        } else {
            log.info("텔레그램 다이제스트 생성 스킵: channel={}, date={}, reason={}",
                channel.getName(), date, result.reason());
        }
        return result;
    }

    private TelegramDigestGenerateResult generateForChannel(Channel channel, LocalDate date) {
        List<TelegramPost> posts = telegramPostReader.findPublishedBetween(
            channel, TelegramPostStatus.SELECTED, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        if (posts.isEmpty()) {
            return TelegramDigestGenerateResult.skipped(channel.getName());
        }

        SummarizeApiResponse response = telegramDigestGenerator.generate(channel, posts);
        telegramDigestAppender.persistResult(channel, date, response);
        return TelegramDigestGenerateResult.success(channel.getName(), posts.size());
    }
}
