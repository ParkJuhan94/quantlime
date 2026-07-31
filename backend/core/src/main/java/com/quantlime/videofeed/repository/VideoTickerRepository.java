package com.quantlime.videofeed.repository;

import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoTicker;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoTickerRepository extends JpaRepository<VideoTicker, Long> {

    List<VideoTicker> findByTickerCode(String tickerCode);

    // 피드 목록 조회 시 영상 N개의 태깅 종목을 한 번에 배치 조회하기 위함(N+1 방지).
    List<VideoTicker> findByVideo_IdIn(List<Long> videoIds);

    List<VideoTicker> findByVideo(Video video);

    // VideoRetentionService - 보존 기간 초과 영상을 지우기 전, video_id를
    // NO_CONSTRAINT FK로만 참조해 DB 캐스케이드가 없는 이 테이블을 먼저 비운다.
    void deleteByVideo_IdIn(List<Long> videoIds);
}
