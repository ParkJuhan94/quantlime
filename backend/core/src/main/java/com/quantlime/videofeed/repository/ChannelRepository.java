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

    List<Channel> findByProfileImageUrlIsNull();

    List<Channel> findAllByOrderByPriorityAsc();

    // 유튜브 전용 파이프라인(FeedCollectionFacade/ChannelSeedInitializer/
    // VideoFeedService)이 텔레그램 채널(Phase 8 P7)을 잘못 집어 YouTube API를
    // 호출하지 않도록 플랫폼으로 한정한 버전. 위 플랫폼 무관 메서드들은
    // 관리자 채널 목록(ChannelQueryService, 전체를 보여줘야 함) 용도로 남겨둔다.
    List<Channel> findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform platform);

    List<Channel> findByPlatformAndProfileImageUrlIsNull(Platform platform);

    // 텔레그램 관리자 채널 목록(TelegramChannelQueryService)용 - 활성/
    // 비활성 전체를 보여줘야 하는 건 ChannelQueryService와 같지만, 유튜브
    // 채널이 섞여 나오면 안 되므로 플랫폼으로 한정한다.
    List<Channel> findByPlatformOrderByPriorityAsc(Platform platform);
}
