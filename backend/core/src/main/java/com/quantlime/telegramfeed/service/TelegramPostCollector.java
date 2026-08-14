package com.quantlime.telegramfeed.service;

import com.quantlime.common.util.SleepUtil;
import com.quantlime.infra.telegram.TelegramApiProperties;
import com.quantlime.infra.telegram.TelegramWebPreviewClient;
import com.quantlime.infra.telegram.dto.TelegramPreviewMessage;
import com.quantlime.infra.telegram.dto.TelegramPreviewPage;
import com.quantlime.telegramfeed.dto.CollectedTelegramPost;
import com.quantlime.telegramfeed.dto.TelegramChannelMeta;
import com.quantlime.telegramfeed.dto.TelegramCollectionOutcome;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.videofeed.domain.Channel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 텔레그램 채널 글 수집(외부 I/O)만 담당한다 - DB 트랜잭션과 절대 섞지
 * 않는다(YoutubeVideoCollector와 동일 원칙, §5 트랜잭션 경계 규칙).
 */
@Component
@RequiredArgsConstructor
public class TelegramPostCollector {

    // VideoFilterService.RETENTION_DAYS와 동일 값 유지(공유 설정 파일이 없는
    // 프로젝트 관례) - 최초 수집 시 이 창을 넘긴 페이지가 나오면 더 파봐야
    // 전부 즉시 하드필터에 걸릴 뿐이라 조기 종료한다.
    private static final int RETENTION_DAYS = 14;
    // 유튜브의 "최초 200개" 상한과 동기: 신규 채널의 오래된 백로그 전체를
    // 훑지 않는다. 실제 제약은 위 보존기간 컷이라(하루 ~20건 채널이면
    // 4페이지=80건이면 14일치를 이미 넘김) 유튜브만큼 크게 잡을 필요가 없다.
    private static final int MAX_PAGES_FIRST_RUN = 4;
    private static final int MAX_PAGES_PER_RUN = 5;

    private final TelegramWebPreviewClient telegramWebPreviewClient;
    private final TelegramPostRepository telegramPostRepository;
    private final TelegramApiProperties telegramApiProperties;

    public TelegramCollectionOutcome collect(Channel channel) {
        String handle = channel.getExternalChannelId();
        return telegramPostRepository.findMaxMessageIdByChannel(channel)
            .map(cursor -> collectIncremental(handle, cursor))
            .orElseGet(() -> collectInitial(handle));
    }

    private TelegramCollectionOutcome collectIncremental(String handle, long cursor) {
        List<CollectedTelegramPost> collected = new ArrayList<>();
        TelegramChannelMeta channelMeta = null;
        long afterId = cursor;
        for (int page = 0; page < MAX_PAGES_PER_RUN; page++) {
            TelegramPreviewPage previewPage = telegramWebPreviewClient.fetchPage(handle, afterId, null);
            if (channelMeta == null) {
                channelMeta = toChannelMeta(previewPage);
            }
            if (previewPage.messages().isEmpty()) {
                break;
            }
            collected.addAll(toCollectedPosts(previewPage.messages()));
            afterId = maxMessageId(previewPage.messages());
            if (page < MAX_PAGES_PER_RUN - 1 && !SleepUtil.sleepMillis(telegramApiProperties.getRequestDelayMs())) {
                break;
            }
        }
        return new TelegramCollectionOutcome(collected, channelMeta);
    }

    private TelegramCollectionOutcome collectInitial(String handle) {
        List<CollectedTelegramPost> collected = new ArrayList<>();
        TelegramChannelMeta channelMeta = null;
        LocalDateTime retentionCutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        Long beforeId = null;
        for (int page = 0; page < MAX_PAGES_FIRST_RUN; page++) {
            TelegramPreviewPage previewPage = telegramWebPreviewClient.fetchPage(handle, null, beforeId);
            if (channelMeta == null) {
                channelMeta = toChannelMeta(previewPage);
            }
            if (previewPage.messages().isEmpty()) {
                break;
            }
            collected.addAll(toCollectedPosts(previewPage.messages()));
            boolean reachedRetentionCutoff = previewPage.messages().stream()
                .anyMatch(message -> message.publishedAt().isBefore(retentionCutoff));
            if (reachedRetentionCutoff) {
                break;
            }
            beforeId = minMessageId(previewPage.messages());
            if (page < MAX_PAGES_FIRST_RUN - 1 && !SleepUtil.sleepMillis(telegramApiProperties.getRequestDelayMs())) {
                break;
            }
        }
        return new TelegramCollectionOutcome(collected, channelMeta);
    }

    private TelegramChannelMeta toChannelMeta(TelegramPreviewPage page) {
        return new TelegramChannelMeta(page.channelTitle(), page.channelPhotoUrl());
    }

    private List<CollectedTelegramPost> toCollectedPosts(List<TelegramPreviewMessage> messages) {
        return messages.stream()
            .map(message -> new CollectedTelegramPost(
                message.externalPostId(), message.messageId(), message.content(),
                message.publishedAt(), message.viewCount(), message.hasMedia()))
            .toList();
    }

    private long maxMessageId(List<TelegramPreviewMessage> messages) {
        return messages.stream().mapToLong(TelegramPreviewMessage::messageId).max().orElseThrow();
    }

    private long minMessageId(List<TelegramPreviewMessage> messages) {
        return messages.stream().min(Comparator.comparingLong(TelegramPreviewMessage::messageId))
            .orElseThrow().messageId();
    }
}
