package com.quantlime.videofeed.repository;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, Long> {

    boolean existsByExternalVideoId(String externalVideoId);

    List<Video> findByChannelAndStatus(Channel channel, VideoStatus status);

    List<Video> findByStatusAndPublishedAtBefore(VideoStatus status, LocalDateTime publishedAt);

    // SELECTED(retryCount=0, 최초 시도)와 FAILED(재시도 대상, retryCount<maxRetryCount)를
    // 한 쿼리로 함께 잡는다 - "자막 없음"처럼 영구적으로 실패하는 영상도 FAILED로
    // 기록되는 동일 상태를 공유해 재시도 상한(TranscriptCollectionFacade 참고) 안에서
    // 반복 시도되지만, 그 낭비가 최대 시도 횟수로 bounded돼 있어 별도의 "재시도
    // 불가" 사유 구분 필드를 추가하지 않았다.
    Slice<Video> findByStatusInAndRetryCountLessThanOrderByPublishedAtAsc(
        List<VideoStatus> statuses, int maxRetryCount, Pageable pageable);
}
