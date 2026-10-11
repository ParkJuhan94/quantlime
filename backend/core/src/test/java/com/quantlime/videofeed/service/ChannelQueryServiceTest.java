package com.quantlime.videofeed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.implement.ChannelReader;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChannelQueryServiceTest {

    @Mock
    private ChannelReader channelReader;

    @Test
    @DisplayName("[채널 목록을 우선순위 오름차순으로 조회한다]")
    void findAllOrderByPriority_returnsChannelsFromRepository() {
        // given
        ChannelQueryService channelQueryService = new ChannelQueryService(channelReader);
        Channel channel = Channel.of(Platform.YOUTUBE, "UCF8AeLlUbEpKju6v1H6p8Eg", "UUF8AeLlUbEpKju6v1H6p8Eg",
            "한국경제TV", 10, new ChannelFilterConfig(300, 1.5, 5, List.of(), List.of()));
        when(channelReader.findAll()).thenReturn(List.of(channel));

        // when
        List<Channel> result = channelQueryService.findAllOrderByPriority();

        // then
        assertThat(result).containsExactly(channel);
        verify(channelReader).findAll();
    }
}
