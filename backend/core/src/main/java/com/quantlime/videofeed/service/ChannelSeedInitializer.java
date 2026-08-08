package com.quantlime.videofeed.service;

import com.quantlime.infra.youtube.YoutubeApiClient;
import com.quantlime.infra.youtube.dto.YoutubeChannelsResponse;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.ChannelFilterConfig;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * v1 대상 채널 3개(한국경제TV/런던고라니/주덕) + 2026-08-06 추가 채널
 * 시딩. Flyway/Liquibase 없이 ddl-auto=update만 쓰는 프로젝트라
 * StockMasterInitializer와 동일한 ApplicationRunner 방식을 그대로
 * 따른다(이미 있는 채널은 skip).
 *
 * <p><b>channelId 검증 완료(2026-07-27)</b>: 아래 3개는
 * `channels.list?part=snippet&id=...`(제목/customUrl 일치)와
 * `channels.list?part=id&forHandle=...`(역조회 ID 일치) 양방향으로
 * 실제 YouTube Data API 호출을 통해 재검증했다.
 * - 한국경제TV: UCF8AeLlUbEpKju6v1H6p8Eg (customUrl=@hkwowtv, 구독자 139만)
 * - 런던고라니=김희욱: UC4-Y6u1a0j2et5k35EQHU0w (customUrl=@gorany, 구독자 12만)
 * - 주덕: UChZFFQS6ThJ_VmuE-Yzao8Q (customUrl=@joodeok, 구독자 18.8만)
 *
 * <p><b>알상무/미과장(2026-08-06 추가)</b>: 사용자가 직접 전달한
 * channelId(각각 UCiDmfbYvuMEVbRxPmFP4sng, UC7JriZyW6E5phUSKfjoaEPA) -
 * 위 3개와 달리 이번 세션에서는 forHandle 역조회 재검증을 하지 않았다(이
 * 세션의 웹 접근 환경에서 YouTube Data API 직접 호출이 불가능했음).
 * 런던고라니/주덕과 동일하게 개인 채널로 판단해 velocity_multiplier=0
 * (중앙값 업로드 속도 필터 없이 길이/제목 필터만 적용)으로 시딩했다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class ChannelSeedInitializer implements ApplicationRunner {

    private final ChannelRepository channelRepository;
    private final YoutubeApiClient youtubeApiClient;

    // run() 자체엔 @Transactional을 걸지 않는다 - seedIfAbsent는 채널별로
    // 독립된 exists 체크+단건 save라 3개를 하나의 트랜잭션으로 묶을 필요가
    // 없고(부분 실패해도 다음 기동 때 나머지가 자연히 채워짐), 아래
    // backfillProfileImagesIfMissing()이 외부 HTTP 호출(유튜브 API)을
    // 포함하는데 이걸 DB 트랜잭션 안에 넣으면 커넥션을 불필요하게 오래
    // 붙잡는다(I/O와 트랜잭션 경계를 분리하는 이 프로젝트의 일관된 설계
    // 원칙, docs/CHANGELOG.md P3/P4 self-invocation 버그 참고).
    @Override
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
        seedIfAbsent(
            "UCiDmfbYvuMEVbRxPmFP4sng", "알상무", 20,
            new ChannelFilterConfig(180, 0.0, 3, List.of(), List.of()));
        seedIfAbsent(
            "UC7JriZyW6E5phUSKfjoaEPA", "미과장", 20,
            new ChannelFilterConfig(180, 0.0, 3, List.of(), List.of()));

        backfillProfileImagesIfMissing();
    }

    // 이 메서드는 run()에서 this.seedIfAbsent(...)로(같은 인스턴스 내부 호출)
    // 불리므로 여기에 @Transactional을 붙여도 프록시를 안 타 무시된다 -
    // 붙이지 않는 이유를 명시적으로 남긴다. 아래 channelRepository.save()
    // 자체가 Spring Data 리포지토리 프록시를 통해 독립적으로 트랜잭셔널하므로
    // 이 메서드 레벨에서 별도로 감쌀 필요가 없다.
    void seedIfAbsent(String externalChannelId, String name, int priority, ChannelFilterConfig filterConfig) {
        if (channelRepository.existsByPlatformAndExternalChannelId(Platform.YOUTUBE, externalChannelId)) {
            return;
        }
        String uploadsPlaylistId = "UU" + externalChannelId.substring(2);
        Channel channel = Channel.of(Platform.YOUTUBE, externalChannelId, uploadsPlaylistId, name, priority, filterConfig);
        channelRepository.save(channel);
        log.info("피드 채널 시딩 완료: name={}, externalChannelId={}", name, externalChannelId);
    }

    // 프로필 사진 백필은 채널 시딩(핵심 기능)과 무관한 부가 기능이라, 유튜브
    // API 장애/쿼터 소진으로 실패해도 앱 기동을 막지 않는다 - 다음 기동 시
    // profile_image_url이 여전히 null인 채널만 다시 시도된다.
    private void backfillProfileImagesIfMissing() {
        List<Channel> channelsMissingImage = channelRepository.findByProfileImageUrlIsNull();
        if (channelsMissingImage.isEmpty()) {
            return;
        }
        try {
            List<String> channelIds = channelsMissingImage.stream().map(Channel::getExternalChannelId).toList();
            Map<String, String> imageUrlByChannelId = fetchProfileImageUrls(channelIds);
            imageUrlByChannelId.forEach(this::persistProfileImageUrl);
        } catch (Exception e) {
            log.warn("채널 프로필 사진 백필에 실패했습니다 - 다음 기동 시 재시도됩니다.", e);
        }
    }

    private Map<String, String> fetchProfileImageUrls(List<String> channelIds) {
        YoutubeChannelsResponse response = youtubeApiClient.getChannels(channelIds);
        return response.items().stream()
            .filter(item -> item.snippet() != null && item.snippet().thumbnails() != null
                && item.snippet().thumbnails().defaultThumbnail() != null)
            .collect(Collectors.toMap(YoutubeChannelsResponse.Item::id,
                item -> item.snippet().thumbnails().defaultThumbnail().url()));
    }

    // findBy...로 얻은 엔티티는 그 조회 호출이 끝나면 detach되므로,
    // updateProfileImageUrl 뮤테이션만으로는 반영되지 않는다 - save()를
    // 명시적으로 다시 호출해야 한다(ChannelVelocityInitializationService의
    // self-invocation 버그와 원인은 다르지만 증상이 같은 "조용히 저장 안
    // 됨" 함정). 이 메서드도 this::persistProfileImageUrl로 self-invocation
    // 되므로 @Transactional을 붙이지 않는다 - 위 seedIfAbsent와 동일한 이유.
    void persistProfileImageUrl(String externalChannelId, String profileImageUrl) {
        channelRepository.findByPlatformAndExternalChannelId(Platform.YOUTUBE, externalChannelId)
            .ifPresent(channel -> {
                channel.updateProfileImageUrl(profileImageUrl);
                channelRepository.save(channel);
            });
    }
}
