package com.quantlime.event.videofeed;

import static org.mockito.Mockito.verify;

import com.quantlime.videofeed.service.LocalTranscriptSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class LocalTranscriptSyncConsumerTest {

    @Mock
    private LocalTranscriptSyncService localTranscriptSyncService;

    @InjectMocks
    private LocalTranscriptSyncConsumer consumer;

    @Test
    @DisplayName("[자막 저장 이벤트를 받으면 해당 videoId로 운영 동기화 서비스를 호출한다]")
    void onVideoTranscribed_delegatesToSyncService() {
        consumer.onVideoTranscribed(VideoTranscribedMessage.of(42L));

        verify(localTranscriptSyncService).syncOne(42L);
    }

    @Test
    @DisplayName("[dev 프로파일에서만 활성화되고 SummarizeRequestConsumer와 다른 컨슈머 그룹으로 같은 토픽을 구독한다]")
    void wiring_devProfileOnly_separateConsumerGroup() throws Exception {
        Profile profile = LocalTranscriptSyncConsumer.class.getAnnotation(Profile.class);
        KafkaListener listener = LocalTranscriptSyncConsumer.class
            .getMethod("onVideoTranscribed", VideoTranscribedMessage.class).getAnnotation(KafkaListener.class);

        assertThat(profile.value()).containsExactly("dev");
        assertThat(listener.topics()).containsExactly(VideoFeedTopics.VIDEO_TRANSCRIBED);
        assertThat(listener.groupId()).isEqualTo("local-prod-sync");
    }
}
