package com.quantlime.videofeed.implement;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link ChannelRepository}의 채널 조회을 감싸는 구현 레이어(Implementation) - 서비스가 Data Access(Repository)를
 * 직접 건드리지 않고 이 컴포넌트를 통해서만 접근하게 한다. 메서드 이름은 Repository와 같게 두어
 * 호출부 변환이 위임 한 겹으로 끝나게 했다.
 */
@Component
@RequiredArgsConstructor
public class ChannelReader {

    private final ChannelRepository channelRepository;

    public boolean existsByPlatformAndExternalChannelId(Platform platform, String externalChannelId) {
        return channelRepository.existsByPlatformAndExternalChannelId(platform, externalChannelId);
    }

    public Optional<Channel> findByPlatformAndExternalChannelId(Platform platform, String externalChannelId) {
        return channelRepository.findByPlatformAndExternalChannelId(platform, externalChannelId);
    }

    public List<Channel> findAllByOrderByPriorityAsc() {
        return channelRepository.findAllByOrderByPriorityAsc();
    }

    public List<Channel> findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform platform) {
        return channelRepository.findByPlatformAndEnabledTrueOrderByPriorityAsc(platform);
    }

    public List<Channel> findByPlatformAndProfileImageUrlIsNull(Platform platform) {
        return channelRepository.findByPlatformAndProfileImageUrlIsNull(platform);
    }

    public Optional<Channel> findById(Long id) {
        return channelRepository.findById(id);
    }
}
