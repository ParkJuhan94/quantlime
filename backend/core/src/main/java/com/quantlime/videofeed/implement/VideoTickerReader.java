package com.quantlime.videofeed.implement;

import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoTicker;
import com.quantlime.videofeed.repository.VideoTickerRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link VideoTickerRepository}의 영상 종목 태그 조회을 감싸는 구현 레이어(Implementation) - 서비스가 Data Access(Repository)를
 * 직접 건드리지 않고 이 컴포넌트를 통해서만 접근하게 한다. 메서드 이름은 Repository와 같게 두어
 * 호출부 변환이 위임 한 겹으로 끝나게 했다.
 */
@Component
@RequiredArgsConstructor
public class VideoTickerReader {

    private final VideoTickerRepository videoTickerRepository;

    public List<VideoTicker> findByVideo(Video video) {
        return videoTickerRepository.findByVideo(video);
    }

    public List<VideoTicker> findByVideo_IdIn(List<Long> videoIds) {
        return videoTickerRepository.findByVideo_IdIn(videoIds);
    }
}
