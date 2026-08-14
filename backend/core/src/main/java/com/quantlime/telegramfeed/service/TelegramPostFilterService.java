package com.quantlime.telegramfeed.service;

import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostStatus;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.videofeed.domain.Channel;
import com.quantlime.videofeed.domain.TelegramFilterConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DISCOVERED 상태 글에 채널별 telegram_filter_config를 적용해 FILTERED_OUT
 * / SELECTED로 분기한다. VideoFilterService(유튜브)와 구조는 같지만
 * velocity/PENDING_REVIEW 단계가 없다 - 텔레그램 하드필터(글자수/키워드)는
 * 게시 즉시 확정된 값만 보므로 유예할 이유가 없다. "게시 직후라 조회수가
 * 불안정"한 문제는 max_per_run 랭킹 기준을 viewCount 대신 charCount로
 * 쓰는 것으로 대체했다(설계 근거는 docs/ROADMAP.md "Phase 8 P7" 참고).
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
        classifyAndSelect(channel, discovered);
    }

    private void classifyAndSelect(Channel channel, List<TelegramPost> candidates) {
        TelegramFilterConfig config = channel.getTelegramFilterConfig();
        List<TelegramPost> eligible = candidates.stream()
            .filter(post -> classify(post, channel, config))
            .toList();
        selectUpToDailyQuota(channel, eligible, config.maxPerRun());
    }

    private boolean classify(TelegramPost post, Channel channel, TelegramFilterConfig config) {
        String hardFilterReason = hardFilterRejectionReason(post, config);
        if (hardFilterReason != null) {
            post.markFilteredOut();
            log.info("텔레그램 글 탈락(하드필터): postId={}, channel={}, reason={}, externalPostId={}",
                post.getId(), channel.getName(), hardFilterReason, post.getExternalPostId());
            return false;
        }
        return true;
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

    // 발행일별로 후보를 나눠 각 날짜마다 독립적으로 max_per_run 상한을
    // 적용한다(VideoFilterService.selectUpToMaxPerRun과 동일 이유 - 로컬
    // 개발처럼 수집이 며칠 걸러 몰아서 돌 때 백로그 전체가 한 번의 상한만
    // 적용받아 영구 탈락하는 문제 방지). 랭킹 기준만 유튜브(viewCount)와
    // 다르게 charCount로 쓴다 - 짧은 속보보다 분석성 긴 글을 우선한다.
    private void selectUpToDailyQuota(Channel channel, List<TelegramPost> eligible, int maxPerRun) {
        Map<LocalDate, List<TelegramPost>> byPublishedDate = eligible.stream()
            .collect(Collectors.groupingBy(post -> post.getPublishedAt().toLocalDate()));
        byPublishedDate.forEach((publishedDate, postsOnDate) ->
            selectUpToDailyQuotaForDate(channel, postsOnDate, maxPerRun, publishedDate));
    }

    // 같은 날짜의 후보가 여러 수집 사이클에 걸쳐 나뉘어 들어올 수 있어 이번
    // 배치의 후보 개수만 보지 않고 그 날짜에 이미 SELECTED된 개수를 DB에서
    // 다시 세어 남은 쿼터만 적용한다 - 그래야 하루 상한이 사이클 횟수와
    // 무관하게 지켜진다.
    private void selectUpToDailyQuotaForDate(Channel channel, List<TelegramPost> postsOnDate, int maxPerRun,
                                              LocalDate publishedDate) {
        int alreadySelected = telegramPostRepository.countByChannelAndStatusAndPublishedAtBetween(
            channel, TelegramPostStatus.SELECTED, publishedDate.atStartOfDay(), publishedDate.plusDays(1).atStartOfDay());
        int remainingQuota = Math.max(maxPerRun - alreadySelected, 0);

        List<TelegramPost> ranked = postsOnDate.stream()
            .sorted(Comparator.comparingInt(TelegramPost::getCharCount).reversed()
                .thenComparing(Comparator.comparingLong(TelegramPost::getMessageId).reversed()))
            .toList();
        for (int i = 0; i < ranked.size(); i++) {
            if (i < remainingQuota) {
                ranked.get(i).markSelected();
            } else {
                ranked.get(i).markFilteredOut();
                log.info("텔레그램 글 탈락(하루 max_per_run 컷): postId={}, channel={}, publishedDate={}, externalPostId={}",
                    ranked.get(i).getId(), channel.getName(), publishedDate, ranked.get(i).getExternalPostId());
            }
        }
    }
}
