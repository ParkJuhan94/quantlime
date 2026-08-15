package com.quantlime.common.startup;

import com.quantlime.common.util.SafeExecutor;
import com.quantlime.market.service.MarketDataRefreshService;
import com.quantlime.telegramfeed.service.TelegramCollectionFacade;
import com.quantlime.telegramfeed.service.TelegramDigestGenerationFacade;
import com.quantlime.telegramfeed.service.TelegramPostRetentionService;
import com.quantlime.videofeed.service.FeedCollectionFacade;
import com.quantlime.videofeed.service.SummaryCollectionFacade;
import com.quantlime.videofeed.service.TranscriptCollectionFacade;
import com.quantlime.videofeed.service.VideoRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 서버가 항상 떠있는 게 아니라 필요할 때만 띄우는 환경(로컬 개발 등)에서는,
 * 정기 스케줄이 도는 시각에 서버가 꺼져 있었으면 그 배치가 통째로 스킵되고
 * 아무도 감지하지 못한 채 넘어갈 수 있다 - 이를 기동 시점 캐치업으로
 * 보완한다. 원래 {@code MarketDataStartupRunner}(가격/스코어 갭필 전용)였던
 * 걸 영상 피드 캐치업(수집→자막→요약→보존기간 정리)까지 함께 묶어 클래스
 * 하나로 일반화했다(2026-08-01) - 둘 다 "정기 스케줄이 놓쳤을 수도 있는
 * 작업을 기동 시 안전하게 재확인한다"는 동일한 목적이라 트리거 지점을
 * 굳이 나눠 관리할 이유가 없었다. 보존 기간(14일) 정리(매일 새벽 3시
 * {@code VideoRetentionScheduler})도 같은 이유로 함께 묶었다: 로컬처럼 그
 * 시각에 서버가 꺼져있는 환경에서는 정리가 그냥 영구히 스킵되기 때문.
 *
 * <p>영상 피드 4단계(수집→자막→요약→보존기간 정리)는 각 서비스가 이미
 * 상태/기간 기반 후보 선정(SELECTED/TRANSCRIBED/발행일 14일 초과 등) +
 * Redis 락(runXxxExclusively)으로 동작해 여러 번 반복 호출해도 안전하다 -
 * 이미 처리(또는 삭제)된 대상은 다음 실행의 후보에서 자연히 빠지므로
 * 기동마다 돌려도 중복 API 호출/중복 삭제가 발생하지 않는다. 가격/스코어
 * 갭필도 동일한 이유로 {@code MarketDataRefreshService.refreshAllExclusively()}
 * (Redis 락)를 쓴다(2026-08-02 추가 - 이전엔 락이 없어 서버가 16:00 근처에
 * 재기동되면 OhlcvCollectorScheduler와 이 캐치업이 동시에 refreshAll()을
 * 시작할 수 있었다). 어느 쪽이든 락이 이미 잡혀 있으면(스케줄러와 겹치는
 * 등) Optional.empty()로 조용히 스킵되므로 여기서 반환값을 따로 분기할
 * 필요가 없다.
 *
 * <p>가격/스코어 갭필과 영상 피드 캐치업은 서로 다른 외부 API/도메인이라
 * 전용 실행기를 하나씩(marketDataCatchUpTaskExecutor/
 * videoFeedCatchUpTaskExecutor) 따로 써서 서로를 대기시키지 않는다. 영상
 * 피드 내부 4단계는 서로 의존하는 순차 파이프라인(자막은 SELECTED 영상이,
 * 요약은 TRANSCRIBED 영상이 있어야 후보가 됨)이라 굳이 스레드를 더
 * 쪼개지 않고 하나의 실행기에서 순서대로 처리한다 - 보존기간 정리는
 * 지울 게 없으면 즉시 끝나는 가벼운 작업이라 별도 실행기를 줄 실익이 없다.
 *
 * <p>텔레그램 피드 캐치업(수집→요약→보존기간 정리, Phase 8 P7-6)도 같은
 * 이유(로컬 개발 환경에서 정기 스케줄 시각에 서버가 꺼져있으면 그 사이클이
 * 영구 스킵됨)로 함께 묶었다 - 자막 단계가 없어 3단계뿐이고, 유튜브와
 * 마찬가지로 서로 다른 외부 API(t.me 스크래핑)를 부르므로 전용 실행기
 * (telegramFeedCatchUpTaskExecutor)를 별도로 쓴다.
 *
 * <p>{@code ChannelSeedInitializer}(채널 시딩)와의 실행 순서는 Spring이
 * 보장해주지 않는다 - 만약 채널 시딩보다 먼저 이 캐치업이 돌면 이번 기동에서는
 * 채널이 0개라 영상 수집이 그냥 아무 일도 안 하고 끝난다. 이는 오류가 아니라
 * 다음 정기 스케줄(또는 다음 재기동)에서 자연히 다시 시도되는 eventually
 * consistent 동작이라 별도 순서 강제(@Order 등)를 추가하지 않았다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class StartupCatchUpRunner implements ApplicationRunner {

    private final MarketDataRefreshService marketDataRefreshService;
    private final FeedCollectionFacade feedCollectionFacade;
    private final TranscriptCollectionFacade transcriptCollectionFacade;
    private final SummaryCollectionFacade summaryCollectionFacade;
    private final VideoRetentionService videoRetentionService;
    private final TelegramCollectionFacade telegramCollectionFacade;
    private final TelegramDigestGenerationFacade telegramDigestGenerationFacade;
    private final TelegramPostRetentionService telegramPostRetentionService;
    private final TaskExecutor marketDataCatchUpTaskExecutor;
    private final TaskExecutor videoFeedCatchUpTaskExecutor;
    private final TaskExecutor telegramFeedCatchUpTaskExecutor;

    @Override
    public void run(ApplicationArguments args) {
        log.info("기동 시 가격/스코어 갭필 및 영상 피드 캐치업(수집→자막→요약→보존기간 정리)을 비동기로 트리거합니다");
        marketDataCatchUpTaskExecutor.execute(() ->
            SafeExecutor.runSafely("기동 시 가격/스코어 갭필",
                () -> marketDataRefreshService.refreshAllExclusively()));
        videoFeedCatchUpTaskExecutor.execute(() ->
            SafeExecutor.runSafely("기동 시 영상 피드 캐치업", this::catchUpVideoFeed));
        telegramFeedCatchUpTaskExecutor.execute(() ->
            SafeExecutor.runSafely("기동 시 텔레그램 피드 캐치업", this::catchUpTelegramFeed));
    }

    // 수집→자막→요약→보존기간 정리 순서를 지켜 순차 호출한다(자막은 SELECTED
    // 영상이, 요약은 TRANSCRIBED 영상이 있어야 후보가 됨 - 보존기간 정리는
    // 순서 의존은 없지만 굳이 별도 스레드를 쓸 만큼 무겁지 않아 마지막에
    // 이어 붙였다). 각 단계의 Optional은 락 스킵 여부만 담고 있어 여기서는
    // 별도로 확인하지 않는다(스킵돼도 다음 정기 스케줄에서 다시 시도됨).
    private void catchUpVideoFeed() {
        feedCollectionFacade.runAllExclusively();
        transcriptCollectionFacade.runBatchExclusively();
        summaryCollectionFacade.runBatchExclusively();
        videoRetentionService.runExclusively();
    }

    // 텔레그램은 자막 단계가 없어(본문이 이미 텍스트) 수집→다이제스트 생성→
    // 보존기간 정리 3단계뿐이다(2026-08-15부로 요약이 글 단위에서 채널×날짜
    // 다이제스트로 바뀌었지만 3단계 구조 자체는 동일).
    private void catchUpTelegramFeed() {
        telegramCollectionFacade.runAllExclusively();
        telegramDigestGenerationFacade.runAllExclusively();
        telegramPostRetentionService.runExclusively();
    }
}
