package com.quantlime.videofeed.service;

import com.quantlime.infra.youtube.YoutubeApiClient;
import com.quantlime.infra.youtube.dto.YoutubePlaylistItemsResponse;
import com.quantlime.infra.youtube.dto.YoutubeVideosResponse;
import com.quantlime.support.DataJpaTestSupport;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

// ChannelVelocityInitializationService는 @DataJpaTest 슬라이스가 스캔하는
// Repository가 아니라 @Service라서 명시적으로 @Import해야 실제 스프링 빈(=AOP
// 프록시)으로 뜬다. Mockito만으로 만든 목(mock) 서비스로는 self-invocation이
// @Transactional을 실제로 우회하는지 검증할 수 없어(목 자체가 프록시/트랜잭션과
// 무관) 반드시 이 방식(DataJpaTest + 실제 빈)으로 재현해야 한다.
@Tag("integration")
@Import(ChannelVelocityInitializationService.class)
class ChannelVelocityInitializationServiceTest extends DataJpaTestSupport {

    @Autowired
    private ChannelVelocityInitializationService channelVelocityInitializationService;

    @Autowired
    private ChannelRepository channelRepository;

    @MockBean
    private YoutubeApiClient youtubeApiClient;

    @AfterEach
    void cleanUp() {
        // @DataJpaTest 기본 롤백에 기대지 않고 이 테스트가 커밋한 데이터를 직접 정리한다
        // (아래 테스트가 앰비언트 트랜잭션을 의도적으로 끄기 때문 - 이유는 테스트 본문 주석 참고)
        channelRepository.deleteAll();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("[median_velocity 산정 결과가 self-invocation을 거치는 영속화 경로를 통해서도 "
        + "실제 DB에 반영된다 - persistMedianVelocity가 @Transactional 프록시를 우회해도 "
        + "값이 유실되면 안 됨]")
    void initializeMedianVelocity_persistsToDatabase() {
        // given: @DataJpaTest 기본값(테스트 전체를 하나의 트랜잭션으로 감싸 롤백)을 이 테스트만
        // NOT_SUPPORTED로 꺼서 실제 운영과 동일한 조건(호출 시점에 앰비언트 트랜잭션이 없는 상태)을
        // 만든다 - 앰비언트 트랜잭션이 있으면 모든 리포지토리 호출이 같은 영속성 컨텍스트를
        // 공유해 self-invocation으로 @Transactional이 우회돼도 dirty checking이 어차피
        // 잡아내므로 버그가 재현되지 않는다(이 버그는 findById 호출이 자신만의 짧은 트랜잭션을
        // 열고 닫아 엔티티가 즉시 detach되는, 앰비언트 트랜잭션이 없을 때만 벌어지는 문제다).
        Channel channel = channelRepository.save(Channel.of(
            Platform.YOUTUBE, "UCtest", "UUtest", "테스트 채널", 10,
            new ChannelFilterConfig(180, 1.5, 5, List.of(), List.of())));
        String publishedAt = Instant.now().minus(10, ChronoUnit.HOURS).toString();

        given(youtubeApiClient.getPlaylistItems("UUtest", null)).willReturn(
            new YoutubePlaylistItemsResponse(null, List.of(
                new YoutubePlaylistItemsResponse.Item(
                    new YoutubePlaylistItemsResponse.Snippet(
                        "영상 제목", publishedAt,
                        new YoutubePlaylistItemsResponse.ResourceId("vid-1"))))));
        given(youtubeApiClient.getVideos(List.of("vid-1"))).willReturn(
            new YoutubeVideosResponse(List.of(
                new YoutubeVideosResponse.Item(
                    "vid-1", new YoutubeVideosResponse.ContentDetails("PT10M"),
                    new YoutubeVideosResponse.Statistics("1000")))));

        // when
        BigDecimal computedMedian = channelVelocityInitializationService
            .initializeMedianVelocity(channel.getId());

        // then: 계산값(1000회/10시간=100) 자체도, DB에서 새로 조회한 값도 100이어야 한다
        assertThat(computedMedian).isEqualByComparingTo(BigDecimal.valueOf(100));
        Channel persisted = channelRepository.findById(channel.getId()).orElseThrow();
        assertThat(persisted.getMedianVelocity()).isNotNull();
        assertThat(persisted.getMedianVelocity()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }
}
