package com.quantlime.videofeed.repository;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, Long> {

    boolean existsByExternalVideoId(String externalVideoId);

    List<Video> findByChannelAndStatus(Channel channel, VideoStatus status);

    List<Video> findByStatusAndPublishedAtBefore(VideoStatus status, LocalDateTime publishedAt);
}
