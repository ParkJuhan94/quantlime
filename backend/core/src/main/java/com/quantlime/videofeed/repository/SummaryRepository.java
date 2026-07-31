package com.quantlime.videofeed.repository;

import com.quantlime.videofeed.domain.Summary;
import com.quantlime.videofeed.domain.Video;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SummaryRepository extends JpaRepository<Summary, Long> {

    boolean existsByVideo(Video video);

    Optional<Summary> findByVideo(Video video);

    // 피드 목록 조회 시 영상 N개의 요약을 한 번에 배치 조회하기 위함(N+1 방지).
    List<Summary> findByVideo_IdIn(List<Long> videoIds);

    // VideoRetentionService - 보존 기간 초과 영상을 지우기 전, video_id를
    // NO_CONSTRAINT FK로만 참조해 DB 캐스케이드가 없는 이 테이블을 먼저 비운다.
    void deleteByVideo_IdIn(List<Long> videoIds);
}
