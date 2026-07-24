package com.quantlime.videofeed.repository;

import com.quantlime.videofeed.domain.Summary;
import com.quantlime.videofeed.domain.Video;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SummaryRepository extends JpaRepository<Summary, Long> {

    boolean existsByVideo(Video video);
}
