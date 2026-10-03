package com.quantlime.videofeed.implement;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import com.quantlime.videofeed.repository.VideoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

/**
 * {@link VideoRepository}의 영상 조회을 감싸는 구현 레이어(Implementation) - 서비스가 Data Access(Repository)를
 * 직접 건드리지 않고 이 컴포넌트를 통해서만 접근하게 한다. 메서드 이름은 Repository와 같게 두어
 * 호출부 변환이 위임 한 겹으로 끝나게 했다.
 */
@Component
@RequiredArgsConstructor
public class VideoReader {

    private final VideoRepository videoRepository;

    public Optional<Video> findById(Long id) {
        return videoRepository.findById(id);
    }

    public List<Video> findAllById(Iterable<Long> ids) {
        return videoRepository.findAllById(ids);
    }

    public Optional<Video> findByIdWithChannel(Long id) {
        return videoRepository.findByIdWithChannel(id);
    }

    public Optional<Video> findByExternalVideoId(String externalVideoId) {
        return videoRepository.findByExternalVideoId(externalVideoId);
    }

    public List<Video> findByChannelAndStatus(Channel channel, VideoStatus status) {
        return videoRepository.findByChannelAndStatus(channel, status);
    }

    public int countByChannelAndStatusAndPublishedAtBetween(Channel channel, VideoStatus status, LocalDateTime start, LocalDateTime end) {
        return videoRepository.countByChannelAndStatusAndPublishedAtBetween(channel, status, start, end);
    }

    public List<Video> findByStatusAndPublishedAtBefore(VideoStatus status, LocalDateTime publishedAt) {
        return videoRepository.findByStatusAndPublishedAtBefore(status, publishedAt);
    }

    public Slice<Video> findTranscribeCandidates(List<VideoStatus> statuses, int maxRetryCount, Pageable pageable) {
        return videoRepository.findTranscribeCandidates(statuses, maxRetryCount, pageable);
    }

    public Slice<Video> findSummarizeCandidates(List<VideoStatus> statuses, int maxRetryCount, Pageable pageable) {
        return videoRepository.findSummarizeCandidates(statuses, maxRetryCount, pageable);
    }

    public Slice<Video> findSummarizedVideos(String tickerCode, Long channelId, LocalDateTime publishedFrom, LocalDateTime publishedTo, Pageable pageable) {
        return videoRepository.findSummarizedVideos(tickerCode, channelId, publishedFrom, publishedTo, pageable);
    }

    public Optional<Video> findSummarizedVideoById(Long videoId) {
        return videoRepository.findSummarizedVideoById(videoId);
    }

    public List<Long> findIdsByPublishedAtBefore(LocalDateTime cutoff) {
        return videoRepository.findIdsByPublishedAtBefore(cutoff);
    }
}
