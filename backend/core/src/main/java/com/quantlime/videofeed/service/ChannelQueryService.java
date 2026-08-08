package com.quantlime.videofeed.service;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelQueryService {

    private final ChannelRepository channelRepository;

    @Transactional(readOnly = true)
    public List<Channel> findAllOrderByPriority() {
        return channelRepository.findAllByOrderByPriorityAsc();
    }
}
