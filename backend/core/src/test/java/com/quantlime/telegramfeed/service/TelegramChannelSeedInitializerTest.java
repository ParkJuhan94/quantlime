package com.quantlime.telegramfeed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.implement.ChannelAppender;
import com.quantlime.videofeed.implement.ChannelReader;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TelegramChannelSeedInitializerTest {

    @Mock
    private ChannelReader channelReader;

    @Mock
    private ChannelAppender channelAppender;

    @InjectMocks
    private TelegramChannelSeedInitializer initializer;

    @Test
    @DisplayName("[아무 채널도 없으면 두 텔레그램 채널을 기본 필터 설정으로 시딩한다]")
    void run_noneRegistered_seedsBothChannels() {
        given(channelReader.isRegistered(Platform.TELEGRAM, "insidertracking")).willReturn(false);
        given(channelReader.isRegistered(Platform.TELEGRAM, "Donmaek")).willReturn(false);

        initializer.run(null);

        ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
        verify(channelAppender, times(2)).save(captor.capture());
        List<Channel> saved = captor.getAllValues();
        assertThat(saved).extracting(Channel::getExternalChannelId).containsExactly("insidertracking", "Donmaek");
        assertThat(saved).allSatisfy(c -> assertThat(c.getPlatform()).isEqualTo(Platform.TELEGRAM));
        // 단신 뉴스 채널은 200자, 장문 에세이 채널은 300자 기준 (2026-08-15 결정)
        assertThat(saved.get(0).getTelegramFilterConfig().minCharCount()).isEqualTo(200);
        assertThat(saved.get(1).getTelegramFilterConfig().minCharCount()).isEqualTo(300);
        assertThat(saved.get(0).getTelegramFilterConfig().contentExclude())
            .containsExactly("광고", "제휴", "이벤트", "쿠폰");
    }

    @Test
    @DisplayName("[이미 등록된 채널은 다시 저장하지 않는다(재기동 멱등)]")
    void run_alreadyRegistered_skipsSave() {
        given(channelReader.isRegistered(Platform.TELEGRAM, "insidertracking")).willReturn(true);
        given(channelReader.isRegistered(Platform.TELEGRAM, "Donmaek")).willReturn(false);

        initializer.run(null);

        verify(channelAppender, times(1)).save(any(Channel.class));
        verify(channelAppender, never()).save(org.mockito.ArgumentMatchers.argThat(
            c -> "insidertracking".equals(c.getExternalChannelId())));
    }
}
