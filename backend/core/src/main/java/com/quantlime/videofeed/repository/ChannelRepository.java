package com.quantlime.videofeed.repository;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Platform;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    boolean existsByPlatformAndExternalChannelId(Platform platform, String externalChannelId);

    Optional<Channel> findByPlatformAndExternalChannelId(Platform platform, String externalChannelId);

    List<Channel> findByEnabledTrueOrderByPriorityAsc();
}
