package com.quantlime.common.startup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.quantlime.market.service.MarketDataRefreshService;
import com.quantlime.telegramfeed.service.TelegramCollectionFacade;
import com.quantlime.telegramfeed.service.TelegramPostRetentionService;
import com.quantlime.telegramfeed.service.TelegramDigestGenerationFacade;
import com.quantlime.videofeed.service.FeedCollectionFacade;
import com.quantlime.videofeed.service.SummaryCollectionFacade;
import com.quantlime.videofeed.service.TranscriptCollectionFacade;
import com.quantlime.videofeed.service.VideoRetentionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class StartupCatchUpRunnerTest {

    @Mock
    private MarketDataRefreshService marketDataRefreshService;

    @Mock
    private FeedCollectionFacade feedCollectionFacade;

    @Mock
    private TranscriptCollectionFacade transcriptCollectionFacade;

    @Mock
    private SummaryCollectionFacade summaryCollectionFacade;

    @Mock
    private VideoRetentionService videoRetentionService;

    @Mock
    private TelegramCollectionFacade telegramCollectionFacade;

    @Mock
    private TelegramDigestGenerationFacade telegramDigestGenerationFacade;

    @Mock
    private TelegramPostRetentionService telegramPostRetentionService;

    @Mock
    private TaskExecutor marketDataCatchUpTaskExecutor;

    @Mock
    private TaskExecutor videoFeedCatchUpTaskExecutor;

    @Mock
    private TaskExecutor telegramFeedCatchUpTaskExecutor;

    // @InjectMocks의 생성자 주입은 타입이 같은 목(TaskExecutor)이 여러 개면
    // 이름이 아니라 타입만으로 매칭을 시도하다 서로 잘못 엮일 수 있어
    // (MarketDataRefreshServiceTest와 동일한 함정), 여기서는 명시적으로
    // 생성자를 호출한다.
    private StartupCatchUpRunner startupCatchUpRunner;

    @BeforeEach
    void setUp() {
        startupCatchUpRunner = new StartupCatchUpRunner(
            marketDataRefreshService, feedCollectionFacade, transcriptCollectionFacade,
            summaryCollectionFacade, videoRetentionService,
            telegramCollectionFacade, telegramDigestGenerationFacade, telegramPostRetentionService,
            marketDataCatchUpTaskExecutor, videoFeedCatchUpTaskExecutor, telegramFeedCatchUpTaskExecutor);
    }

    @Test
    @DisplayName("[기동 시 가격/스코어 갭필, 영상 피드 캐치업, 텔레그램 피드 캐치업을 각각 전용 실행기에 제출한다]")
    void run_submitsEachCatchUpTaskToItsOwnDedicatedExecutor() {
        // given: 실행기가 즉시 동기 실행하는 것처럼 스텁(제출 자체만 검증하면 충분)
        willAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).given(marketDataCatchUpTaskExecutor).execute(any());
        willAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).given(videoFeedCatchUpTaskExecutor).execute(any());
        willAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).given(telegramFeedCatchUpTaskExecutor).execute(any());

        // when
        startupCatchUpRunner.run(null);

        // then
        verify(marketDataRefreshService).refreshAllExclusively();
        verify(feedCollectionFacade).runAllExclusively();
        verify(transcriptCollectionFacade).runBatchExclusively();
        verify(summaryCollectionFacade).runBatchExclusively();
        verify(videoRetentionService).runExclusively();
        verify(telegramCollectionFacade).runAllExclusively();
        verify(telegramDigestGenerationFacade).runAllExclusively();
        verify(telegramPostRetentionService).runExclusively();
    }

    @Test
    @DisplayName("[실행기 제출만 하고 실제로 실행시키지 않으면 캐치업 로직이 호출되지 않는다]")
    void run_withoutExecutorRunningTask_doesNotInvokeCatchUpLogic() {
        // when
        startupCatchUpRunner.run(null);

        // then
        verify(marketDataCatchUpTaskExecutor).execute(any());
        verify(videoFeedCatchUpTaskExecutor).execute(any());
        verify(telegramFeedCatchUpTaskExecutor).execute(any());
        verifyNoInteractions(marketDataRefreshService, feedCollectionFacade,
            transcriptCollectionFacade, summaryCollectionFacade, videoRetentionService,
            telegramCollectionFacade, telegramDigestGenerationFacade, telegramPostRetentionService);
    }
}
