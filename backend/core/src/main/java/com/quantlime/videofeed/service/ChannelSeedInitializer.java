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
 * <p><b>channelId 검증 상태(중요)</b>: 아래 3개 채널ID는 유튜브 페이지를
 * 직접 열어 확인한 게 아니라 웹 검색 스니펫(vidiq/noxinfluencer/유튜브
 * 검색 결과 링크)만으로 교차 확인한 값이다 - 이 세션에서는 유튜브
 * 도메인 자체가 프록시에서 403으로 막혀 있어 채널 페이지나
 * feeds/videos.xml로 직접 재검증하지 못했다. 실제 운영 투입 전
 * `GET channels.list?part=id&forHandle=@핸들&key=API_KEY`로 반드시
 * 재확인할 것(§4 채널ID 확보 방법 2번).
 * - 한국경제TV(@hkwowtv): UCF8AeLlUbEpKju6v1H6p8Eg (구독자 수·설명이
 *   일치하는 vidiq 통계 페이지에서 확인, 상대적으로 신뢰도 높음)
 * - 런던고라니=김희욱(@gorany): UC4-Y6u1a0j2et5k35EQHU0w (noxinfluencer
 *   분석 페이지 제목에서만 확인, 교차 출처 없음)
 * - 주덕: UChZFFQS6ThJ_VmuE-Yzao8Q (검색 결과의 유튜브 채널 링크 +
 *   socialerus 분석 페이지 2곳에서 동일 ID 확인)
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
