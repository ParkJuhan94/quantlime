package com.quantlime.videofeed.service;

import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * v1 대상 채널 3개(한국경제TV/런던고라니/주덕) 시딩. Flyway/Liquibase 없이
 * ddl-auto=update만 쓰는 프로젝트라 StockMasterInitializer와 동일한
 * ApplicationRunner 방식을 그대로 따른다(이미 있는 채널은 skip).
 *
 * <p><b>channelId 검증 완료(2026-07-27)</b>: 아래 3개는
 * `channels.list?part=snippet&id=...`(제목/customUrl 일치)와
 * `channels.list?part=id&forHandle=...`(역조회 ID 일치) 양방향으로
 * 실제 YouTube Data API 호출을 통해 재검증했다.
 * - 한국경제TV: UCF8AeLlUbEpKju6v1H6p8Eg (customUrl=@hkwowtv, 구독자 139만)
 * - 런던고라니=김희욱: UC4-Y6u1a0j2et5k35EQHU0w (customUrl=@gorany, 구독자 12만)
 * - 주덕: UChZFFQS6ThJ_VmuE-Yzao8Q (customUrl=@joodeok, 구독자 18.8만)
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class ChannelSeedInitializer implements ApplicationRunner {

    private final ChannelRepository channelRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIfAbsent(
            "UCF8AeLlUbEpKju6v1H6p8Eg", "한국경제TV", 10,
            new ChannelFilterConfig(300, 1.5, 5,
                List.of("속보", "시황", "마감", "브리핑", "LIVE"), List.of()));
        seedIfAbsent(
            "UC4-Y6u1a0j2et5k35EQHU0w", "런던고라니", 20,
            new ChannelFilterConfig(180, 0.0, 3, List.of(), List.of()));
        seedIfAbsent(
            "UChZFFQS6ThJ_VmuE-Yzao8Q", "주덕", 20,
            new ChannelFilterConfig(180, 0.0, 3, List.of(), List.of()));
    }

    private void seedIfAbsent(String externalChannelId, String name, int priority, ChannelFilterConfig filterConfig) {
        if (channelRepository.existsByPlatformAndExternalChannelId(Platform.YOUTUBE, externalChannelId)) {
            return;
        }
        String uploadsPlaylistId = "UU" + externalChannelId.substring(2);
        Channel channel = Channel.of(Platform.YOUTUBE, externalChannelId, uploadsPlaylistId, name, priority, filterConfig);
        channelRepository.save(channel);
        log.info("피드 채널 시딩 완료: name={}, externalChannelId={}", name, externalChannelId);
    }
}
