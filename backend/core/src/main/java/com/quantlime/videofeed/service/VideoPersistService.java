package com.quantlime.videofeed.service;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.dto.CollectedVideo;
import com.quantlime.videofeed.repository.VideoRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB 쓰기만 담당(외부 I/O 없음) - 스케줄러 중복 실행으로 같은 영상이 두 번
 * 들어와도 external_video_id UNIQUE + 저장 전 존재 확인으로 멱등하게
 * 방어한다(§5 멱등성 규칙).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoPersistService {

    private final VideoRepository videoRepository;

    @Transactional
    public int upsertAll(Channel channel, List<CollectedVideo> collectedVideos) {
        int insertedCount = 0;
        for (CollectedVideo collected : collectedVideos) {
            if (videoRepository.existsByExternalVideoId(collected.externalVideoId())) {
                continue;
            }
            LocalDateTime viewCountCheckedAt = collected.viewCount() != null ? LocalDateTime.now() : null;
            Video video = Video.of(
                channel,
                collected.externalVideoId(),
                collected.title(),
                collected.publishedAt(),
                collected.durationSec(),
                collected.viewCount(),
                viewCountCheckedAt);
            videoRepository.save(video);
            insertedCount++;
        }
        log.info("영상 적재 완료: channel={}, 신규={}, 전체={}",
            channel.getName(), insertedCount, collectedVideos.size());
        return insertedCount;
    }
}
