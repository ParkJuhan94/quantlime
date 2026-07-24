package com.quantlime.videofeed.domain;

import com.quantlime.common.domain.TimeBaseEntity;
import com.quantlime.videofeed.domain.converter.ChannelFilterConfigConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "channel", uniqueConstraints = {
    @UniqueConstraint(name = "uk_channel", columnNames = {"platform", "external_channel_id"})
})
@Getter
@NoArgsConstructor(access = PROTECTED)
public class Channel extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "channel_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private Platform platform;

    // 스프레드시트/DB "channel_id"라는 이름을 이 프로젝트의 PK 네이밍
    // 컨벤션({entity}_id)이 이미 쓰고 있어, 유튜브/텔레그램이 발급한
    // 외부 채널 식별자는 external_channel_id로 구분해 저장한다.
    @Column(name = "external_channel_id", nullable = false, length = 100)
    private String externalChannelId;

    @Column(name = "uploads_playlist_id", length = 100)
    private String uploadsPlaylistId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Convert(converter = ChannelFilterConfigConverter.class)
    @Column(name = "filter_config", nullable = false, columnDefinition = "json")
    private ChannelFilterConfig filterConfig;

    @Column(name = "median_velocity", precision = 12, scale = 2)
    private BigDecimal medianVelocity;

    @Column(name = "last_collected_at")
    private LocalDateTime lastCollectedAt;

    @Builder
    private Channel(Platform platform, String externalChannelId, String uploadsPlaylistId,
                     String name, boolean enabled, int priority, ChannelFilterConfig filterConfig) {
        validateChannel(platform, externalChannelId, name, filterConfig);
        this.platform = platform;
        this.externalChannelId = externalChannelId;
        this.uploadsPlaylistId = uploadsPlaylistId;
        this.name = name;
        this.enabled = enabled;
        this.priority = priority;
        this.filterConfig = filterConfig;
    }

    public static Channel of(Platform platform, String externalChannelId, String uploadsPlaylistId,
                              String name, int priority, ChannelFilterConfig filterConfig) {
        return Channel.builder()
            .platform(platform)
            .externalChannelId(externalChannelId)
            .uploadsPlaylistId(uploadsPlaylistId)
            .name(name)
            .enabled(true)
            .priority(priority)
            .filterConfig(filterConfig)
            .build();
    }

    public void updateLastCollectedAt(LocalDateTime lastCollectedAt) {
        Assert.notNull(lastCollectedAt, "마지막 수집 시각은 필수입니다.");
        this.lastCollectedAt = lastCollectedAt;
    }

    public void updateMedianVelocity(BigDecimal medianVelocity) {
        Assert.notNull(medianVelocity, "중앙값 업로드 속도는 필수입니다.");
        this.medianVelocity = medianVelocity;
    }

    private void validateChannel(Platform platform, String externalChannelId, String name,
                                  ChannelFilterConfig filterConfig) {
        Assert.notNull(platform, "플랫폼은 필수입니다.");
        Assert.hasText(externalChannelId, "외부 채널 ID는 필수입니다.");
        Assert.hasText(name, "채널명은 필수입니다.");
        Assert.notNull(filterConfig, "필터 설정은 필수입니다.");
    }
}
