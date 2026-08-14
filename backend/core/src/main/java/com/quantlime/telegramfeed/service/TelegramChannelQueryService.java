package com.quantlime.telegramfeed.service;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// ChannelQueryService(유튜브)와 달리 Platform.TELEGRAM으로 한정한다 -
// 관리자 텔레그램 채널 목록에 유튜브 채널이 섞이면 안 되므로, 플랫폼
// 무관 findAllByOrderByPriorityAsc()를 그대로 쓰지 않는다.
@Service
@RequiredArgsConstructor
public class TelegramChannelQueryService {

    private final ChannelRepository channelRepository;

    @Transactional(readOnly = true)
    public List<Channel> findAllOrderByPriority() {
        return channelRepository.findByPlatformOrderByPriorityAsc(Platform.TELEGRAM);
    }
}
