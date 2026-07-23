package com.quantlime.videofeed.repository;

import com.quantlime.videofeed.domain.Transcript;
import com.quantlime.videofeed.domain.Video;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscriptRepository extends JpaRepository<Transcript, Long> {

    boolean existsByVideo(Video video);
}
