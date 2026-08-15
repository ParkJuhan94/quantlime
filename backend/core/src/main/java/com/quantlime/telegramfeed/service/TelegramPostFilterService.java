package com.quantlime.telegramfeed.service;

import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostStatus;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DISCOVERED 상태 글에 채널별 telegram_filter_config를 적용해 FILTERED_OUT
 * / SELECTED로 분기한다. VideoFilterService(유튜브)와 구조는 같지만
 * velocity/PENDING_REVIEW 단계가 없다 - 텔레그램 하드필터(글자수/키워드)는
 * 게시 즉시 확정된 값만 보므로 유예할 이유가 없다.
 *
 * <p>2026-08-15부로 max_per_run 기반 하루 상한 랭킹(charCount desc 정렬 후
 * 상위 N개만 SELECTED)을 제거했다 - 요약 파이프라인이 글 단위 개별 요약에서
 * 채널×날짜 단위 다이제스트(그날 SELECTED된 글 전부를 합쳐 요약)로 바뀌면서,
 * "하루에 몇 개까지 뽑을지"라는 개념 자체가 무의미해졌다(전부가 다이제스트
 * 재료가 됨). 이제 하드필터를 통과하면 그대로 SELECTED다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramPostFilterService {

    // TelegramPostCollector.RETENTION_DAYS/VideoRetentionService.RETENTION_DAYS와
    // 동일 값 유지(공유 설정 파일이 없는 프로젝트 관례).
    private static final int RETENTION_DAYS = 14;

    private final TelegramPostRepository telegramPostRepository;

    @Transactional
    public void applyFilters(Channel channel) {
        List<TelegramPost> discovered =
            telegramPostRepository.findByChannelAndStatus(channel, TelegramPostStatus.DISCOVERED);
        TelegramFilterConfig config = channel.getTelegramFilterConfig();
        for (TelegramPost post : discovered) {
            classify(post, channel, config);
        }
    }

    private void classify(TelegramPost post, Channel channel, TelegramFilterConfig config) {
        String hardFilterReason = hardFilterRejectionReason(post, config);
        if (hardFilterReason != null) {
            post.markFilteredOut();
            log.info("텔레그램 글 탈락(하드필터): postId={}, channel={}, reason={}, externalPostId={}",
                post.getId(), channel.getName(), hardFilterReason, post.getExternalPostId());
            return;
        }
        post.markSelected();
    }

    private String hardFilterRejectionReason(TelegramPost post, TelegramFilterConfig config) {
        LocalDateTime retentionCutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        if (post.getPublishedAt().isBefore(retentionCutoff)) {
            return "TOO_OLD_FOR_RETENTION(publishedAt=%s)".formatted(post.getPublishedAt());
        }
        if (post.getCharCount() < config.minCharCount()) {
            return "MIN_CHAR_COUNT(charCount=%d < %d)".formatted(post.getCharCount(), config.minCharCount());
        }
        String content = post.getContent().toLowerCase(Locale.KOREAN);
        String excludedKeyword = config.contentExclude().stream()
            .filter(keyword -> content.contains(keyword.toLowerCase(Locale.KOREAN)))
            .findFirst()
            .orElse(null);
        if (excludedKeyword != null) {
            return "CONTENT_EXCLUDE(keyword=%s)".formatted(excludedKeyword);
        }
        if (!config.contentInclude().isEmpty()
            && config.contentInclude().stream().noneMatch(keyword -> content.contains(keyword.toLowerCase(Locale.KOREAN)))) {
            return "CONTENT_INCLUDE_MISSING";
        }
        return null;
    }
}
