package com.quantlime.videofeed.repository;

import com.quantlime.videofeed.domain.VideoTicker;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoTickerRepository extends JpaRepository<VideoTicker, Long> {

    List<VideoTicker> findByTickerCode(String tickerCode);
}
