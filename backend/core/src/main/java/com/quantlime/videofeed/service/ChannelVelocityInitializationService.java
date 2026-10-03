package com.quantlime.videofeed.service;

import com.quantlime.common.exception.NotFoundException;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.exception.VideoFeedErrorCode;
import com.quantlime.videofeed.implement.ChannelAppender;
import com.quantlime.videofeed.implement.ChannelReader;
import com.quantlime.videofeed.implement.YoutubeMetadataCollector;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채널별 최근 30개 업로드의 views/hours 중앙값을 산정한다(§8 첫 작업
 * 순서 7번). velocity_multiplier가 0인 채널(개인 채널)은 filter_config
 * 자체가 velocity 검사를 건너뛰므로 이 값이 없어도 무방하지만, 나중에
 * multiplier를 바꿀 수도 있어 모든 채널에 대해 계산해둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelVelocityInitializationService {

    private static final int SAMPLE_SIZE = 30;

    private final YoutubeMetadataCollector youtubeMetadataCollector;
    private final ChannelReader channelReader;
    private final ChannelAppender channelAppender;

    public BigDecimal initializeMedianVelocity(Long channelId) {
        Channel channel = channelReader.findById(channelId)
            .orElseThrow(() -> new NotFoundException(VideoFeedErrorCode.NOT_FOUND_CHANNEL));

        List<BigDecimal> velocities = youtubeMetadataCollector.fetchRecentVelocities(channel.getUploadsPlaylistId(), SAMPLE_SIZE);
        BigDecimal median = median(velocities);
        persistMedianVelocity(channel.getId(), median);
        log.info("중앙값 업로드 속도 산정 완료: channel={}, median={}, sampleSize={}",
            channel.getName(), median, velocities.size());
        return median;
    }

    private BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int size = sorted.size();
        int mid = size / 2;
        if (size % 2 == 1) {
            return sorted.get(mid);
        }
        return sorted.get(mid - 1).add(sorted.get(mid))
            .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    @Transactional
    void persistMedianVelocity(Long channelId, BigDecimal median) {
        // initializeMedianVelocity()가 같은 빈 내부에서 이 메서드를 self-invocation으로
        // 호출하기 때문에 이 @Transactional은 프록시를 거치지 않아 실제로는 적용되지
        // 않는다 - findById로 가져온 엔티티가 트랜잭션 없이 곧바로 detached 상태가 돼
        // updateMedianVelocity() 변경분이 그냥 버려지던 버그가 있었다. save()를 명시적으로
        // 호출해 리포지토리 메서드 자체의 트랜잭션 경계로 merge/update가 실제 반영되게 함.
        Channel channel = channelReader.findById(channelId)
            .orElseThrow(() -> new NotFoundException(VideoFeedErrorCode.NOT_FOUND_CHANNEL));
        channel.updateMedianVelocity(median);
        channelAppender.save(channel);
    }
}
