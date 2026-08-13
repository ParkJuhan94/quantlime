package com.quantlime.videofeed.service;

import com.quantlime.support.ApiTestSupport;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.Summary;
import com.quantlime.videofeed.domain.Transcript;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoTicker;
import com.quantlime.videofeed.dto.CollectedVideo;
import com.quantlime.videofeed.repository.ChannelRepository;
import com.quantlime.videofeed.repository.SummaryRepository;
import com.quantlime.videofeed.repository.TranscriptRepository;
import com.quantlime.videofeed.repository.VideoRepository;
import com.quantlime.videofeed.repository.VideoTickerRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * VideoRetentionService/FeedCollectionFacade는 각각 락 획득 후 자기 자신의 다른
 * 메서드를 self-invocation으로 호출하는 구조라(VideoRetentionService
 * .runExclusively -> this::deleteVideosOlderThanRetention, FeedCollectionFacade
 * .collectChannel -> this.updateLastCollectedAt) 그 호출 대상 메서드 자체엔
 * @Transactional을 붙여도 프록시를 안 타 무시된다.
 *
 * <p>DataJpaTestSupport(@DataJpaTest) 기반 테스트로는 이 self-invocation 경로를
 * 제대로 검증할 수 없다 - 테스트 메서드 전체가 이미 트랜잭션으로 감싸여 있어
 * self-invocation이 있든 없든 항상 "우연히" 성공하기 때문이다
 * (VideoRetentionServiceTest의 기존 주석이 이 한계를 스스로 인정하고 있다).
 * 실제 운영 경로와 동일한 프록시 체인을 태우려면 클래스 레벨 트랜잭션 래핑이
 * 없는 ApiTestSupport(@SpringBootTest)가 필요하다(2026-08-10).
 *
 * <p>이 클래스로 두 지점을 직접 되돌려 검증한 결과:
 * <ul>
 *   <li>{@code runExclusively_...}: VideoRetentionDeleteService.deleteBatch()를
 *       일부러 package-private으로 되돌려도 예외 없이 삭제가 성공했다 - "package-private이면
 *       AnnotationTransactionAttributeSource의 publicMethodsOnly 기본값 때문에
 *       @Transactional이 무시될 것"이라는 추정이 이 앱에서는 틀렸다는 뜻이다
 *       (VideoRetentionDeleteService 클래스 javadoc 참고). 그래도 public은 유지한다
 *       (TranscriptPersistService/SummaryPersistService와의 패턴 일관성 +
 *       향후 Spring 버전 변경에 대한 방어).
 *   <li>{@code runAll_...}: FeedCollectionFacade.updateLastCollectedAt()에서
 *       channelRepository.save(channel) 호출을 일부러 제거하면 실제로 테스트가
 *       실패했다(channel.lastCollectedAt이 null로 남음) - 이건 진짜 버그였다.
 * </ul>
 */
@Tag("integration")
class VideoFeedTransactionProxyIntegrationTest extends ApiTestSupport {

    @Autowired
    private VideoRetentionService videoRetentionService;

    @Autowired
    private FeedCollectionFacade feedCollectionFacade;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private TranscriptRepository transcriptRepository;

    @Autowired
    private SummaryRepository summaryRepository;

    @Autowired
    private VideoTickerRepository videoTickerRepository;

    // FeedCollectionFacade.runAll()이 실제로 유튜브 API를 부르지 않도록
    // 수집/적재/필터링 협력 빈만 스텁한다 - 검증 대상은 그 뒤에 실행되는
    // updateLastCollectedAt(self-invocation) 하나뿐이다.
    @MockBean
    private YoutubeVideoCollector youtubeVideoCollector;

    @MockBean
    private VideoPersistService videoPersistService;

    @MockBean
    private VideoFilterService videoFilterService;

    private Channel seedChannel(String suffix) {
        return channelRepository.save(Channel.of(Platform.YOUTUBE, "UC" + suffix, "UU" + suffix,
            "테스트 채널" + suffix, 10, new ChannelFilterConfig(180, 1.5, 5, List.of(), List.of())));
    }

    @Test
    @DisplayName("[runExclusively()는 self-invocation 경로를 거쳐도 예외 없이 보존기간 초과 "
        + "영상과 자식 데이터를 실제로 삭제한다]")
    void runExclusively_deletesOldVideoThroughRealProxyChain() {
        // given
        Channel channel = seedChannel("old");
        Video oldVideo = videoRepository.save(Video.of(
            channel, "vid-old", "제목", LocalDateTime.now().minusDays(15), 300, 100L, LocalDateTime.now()));
        transcriptRepository.save(Transcript.of(oldVideo, "youtube_auto_caption", "ko", "자막", 2));
        summaryRepository.save(Summary.of(oldVideo, "gemini-3.5-flash-lite",
            "{\"summary\":\"요약\",\"key_points\":[],\"mentioned_tickers\":[],\"caveat\":\"고지\"}", 100, 50));
        videoTickerRepository.save(VideoTicker.of(oldVideo, "005930", "삼성전자", "BULLISH", BigDecimal.valueOf(0.8)));

        // when
        Optional<Integer> result = videoRetentionService.runExclusively();

        // then
        assertThat(result).contains(1);
        assertThat(videoRepository.findById(oldVideo.getId())).isEmpty();
        assertThat(transcriptRepository.findByVideo(oldVideo)).isEmpty();
        assertThat(summaryRepository.findByVideo(oldVideo)).isEmpty();
        assertThat(videoTickerRepository.findByVideo(oldVideo)).isEmpty();
    }

    @Test
    @DisplayName("[runAll()은 self-invocation 경로(updateLastCollectedAt)를 거쳐도 "
        + "channel.lastCollectedAt을 실제로 저장한다 - save() 누락으로 변경분이 "
        + "조용히 버려지던 버그의 회귀 테스트]")
    void runAll_persistsLastCollectedAtThroughRealProxyChain() {
        // given
        Channel channel = seedChannel("collect");
        given(youtubeVideoCollector.collect(any())).willReturn(List.<CollectedVideo>of());
        given(videoPersistService.upsertAll(any(), any())).willReturn(0);

        // when
        feedCollectionFacade.runAll();

        // then
        Channel reloaded = channelRepository.findById(channel.getId()).orElseThrow();
        assertThat(reloaded.getLastCollectedAt()).isNotNull();
    }
}
