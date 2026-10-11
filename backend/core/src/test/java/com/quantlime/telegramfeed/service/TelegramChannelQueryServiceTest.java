package com.quantlime.telegramfeed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.implement.ChannelReader;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TelegramChannelQueryServiceTest {

    @Mock
    private ChannelReader channelReader;

    @InjectMocks
    private TelegramChannelQueryService service;

    @Test
    @DisplayName("[플랫폼을 TELEGRAM으로 한정해 우선순위순으로 조회한다 - 유튜브 채널이 관리자 텔레그램 목록에 섞이지 않게]")
    void findAllOrderByPriority_scopesToTelegramPlatform() {
        // given
        Channel channel = Channel.of(Platform.TELEGRAM, "tg", "tg", "텔레그램 채널", 10,
            new ChannelFilterConfig(0, 0.0, 0, List.of(), List.of()));
        given(channelReader.findByPlatform(Platform.TELEGRAM)).willReturn(List.of(channel));

        // when & then
        assertThat(service.findAllOrderByPriority()).containsExactly(channel);
        verify(channelReader).findByPlatform(Platform.TELEGRAM);
    }
}
