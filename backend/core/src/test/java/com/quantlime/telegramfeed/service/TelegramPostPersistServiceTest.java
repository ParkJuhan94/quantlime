package com.quantlime.telegramfeed.service;

import com.quantlime.telegramfeed.dto.CollectedTelegramPost;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TelegramPostPersistServiceTest {

    @Mock
    private TelegramPostRepository telegramPostRepository;

    @InjectMocks
    private TelegramPostPersistService telegramPostPersistService;

    @Test
    @DisplayName("[이미 저장된 external_post_id는 건너뛰고 신규 글만 적재한다(스케줄러 중복 실행 방어)]")
    void upsertAll_skipsExistingPosts() {
        // given
        Channel channel = Channel.ofTelegram("testhandle", "테스트 채널", 30,
            new TelegramFilterConfig(300, 5, List.of(), List.of()));
        CollectedTelegramPost existing = new CollectedTelegramPost(
            "testhandle/1", 1L, "이미 있는 게시물", LocalDateTime.now(), 100L, false);
        CollectedTelegramPost fresh = new CollectedTelegramPost(
            "testhandle/2", 2L, "새 게시물", LocalDateTime.now(), 200L, false);
        given(telegramPostRepository.existsByExternalPostId("testhandle/1")).willReturn(true);
        given(telegramPostRepository.existsByExternalPostId("testhandle/2")).willReturn(false);

        // when
        int insertedCount = telegramPostPersistService.upsertAll(channel, List.of(existing, fresh));

        // then
        assertThat(insertedCount).isEqualTo(1);
        verify(telegramPostRepository, times(1)).save(any());
        verify(telegramPostRepository, never()).existsByExternalPostId("testhandle/3");
    }
}
