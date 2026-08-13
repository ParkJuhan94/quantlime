package com.quantlime.telegramfeed.service;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 텔레그램 채널(Phase 8 P7) 초기 시딩. ChannelSeedInitializer(유튜브)와
 * 합치지 않는다 - 그쪽은 run() 끝에서 YouTube channels.list로 프로필
 * 사진을 백필하는데, 여기에 텔레그램 핸들이 섞이면 안 된다(P7-0에서 이미
 * 그 반대 방향 버그를 고쳤다 - videofeed 파이프라인이 텔레그램 채널을
 * 잘못 집는 문제, docs/ROADMAP.md "Phase 8 P7" 참고). 텔레그램 채널의
 * 프로필 사진은 별도 백필 로직 없이 매 수집(P7-2)마다 og:image로 갱신된다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class TelegramChannelSeedInitializer implements ApplicationRunner {

    private static final List<String> DEFAULT_CONTENT_EXCLUDE = List.of("광고", "제휴", "이벤트", "쿠폰");

    private final ChannelRepository channelRepository;

    // 채널별로 독립된 exists 체크 + 단건 save라 하나의 트랜잭션으로 묶을
    // 필요가 없다(ChannelSeedInitializer.run()과 동일한 이유).
    @Override
    public void run(ApplicationArguments args) {
        seedIfAbsent("insidertracking", "미국 주식 인사이더", 30,
            new TelegramFilterConfig(300, 2, DEFAULT_CONTENT_EXCLUDE, List.of()));
        seedIfAbsent("Donmaek", "돈맥경화 연구소", 30,
            new TelegramFilterConfig(300, 2, DEFAULT_CONTENT_EXCLUDE, List.of()));
    }

    // channelRepository.save() 자체가 Spring Data 리포지토리 프록시를 통해
    // 독립적으로 트랜잭셔널하므로, run()에서 self-invocation되는 이 메서드에
    // 별도로 @Transactional을 붙일 필요가 없다(ChannelSeedInitializer와
    // 동일한 패턴·이유).
    void seedIfAbsent(String handle, String name, int priority, TelegramFilterConfig filterConfig) {
        if (channelRepository.existsByPlatformAndExternalChannelId(Platform.TELEGRAM, handle)) {
            return;
        }
        Channel channel = Channel.ofTelegram(handle, name, priority, filterConfig);
        channelRepository.save(channel);
        log.info("텔레그램 채널 시딩 완료: name={}, handle={}", name, handle);
    }
}
