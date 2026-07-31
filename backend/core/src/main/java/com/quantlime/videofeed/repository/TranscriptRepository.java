package com.quantlime.videofeed.repository;

import com.quantlime.videofeed.domain.Transcript;
import com.quantlime.videofeed.domain.Video;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscriptRepository extends JpaRepository<Transcript, Long> {

    boolean existsByVideo(Video video);

    Optional<Transcript> findByVideo(Video video);

    // VideoRetentionService - 보존 기간 초과 영상을 지우기 전, video_id를
    // NO_CONSTRAINT FK로만 참조해 DB 캐스케이드가 없는 이 테이블을 먼저 비운다.
    void deleteByVideo_IdIn(List<Long> videoIds);
}
