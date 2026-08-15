package com.quantlime.telegramfeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostTicker;
import com.quantlime.telegramfeed.domain.TelegramSummary;
import com.quantlime.telegramfeed.dto.TelegramSummaryPayload;
import com.quantlime.telegramfeed.dto.mapper.TelegramFeedMapper;
import com.quantlime.telegramfeed.dto.response.TelegramFeedChannelResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedDetailResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedPostResponse;
import com.quantlime.telegramfeed.exception.TelegramFeedErrorCode;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.telegramfeed.repository.TelegramPostTickerRepository;
import com.quantlime.telegramfeed.repository.TelegramSummaryRepository;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// VideoFeedService(유튜브)와 구조적으로 동일 - 공개 조회 전용, 관리자 정보(필터
// 설정/lastCollectedAt 등)는 노출하지 않는다.
@Service
@RequiredArgsConstructor
public class TelegramFeedService {

    private final TelegramPostRepository telegramPostRepository;
    private final TelegramSummaryRepository telegramSummaryRepository;
    private final TelegramPostTickerRepository telegramPostTickerRepository;
    private final ChannelRepository channelRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Slice<TelegramFeedPostResponse> getPosts(
        String tickerCode, Long channelId, LocalDate date, Pageable pageable) {
        LocalDateTime publishedFrom = date == null ? null : date.atStartOfDay();
        LocalDateTime publishedTo = date == null ? null : date.plusDays(1).atStartOfDay();
        Slice<TelegramPost> posts = telegramPostRepository.findSummarizedPosts(
            tickerCode, channelId, publishedFrom, publishedTo, pageable);

        List<Long> postIds = posts.getContent().stream().map(TelegramPost::getId).toList();
        Map<Long, String> summaryByPostId = toSummaryTextMap(telegramSummaryRepository.findByTelegramPost_IdIn(postIds));
        Map<Long, List<TelegramPostTicker>> tickersByPostId =
            groupByPostId(telegramPostTickerRepository.findByTelegramPost_IdIn(postIds));

        return posts.map(post -> TelegramFeedMapper.toPostResponse(
            post,
            summaryByPostId.getOrDefault(post.getId(), ""),
            tickersByPostId.getOrDefault(post.getId(), List.of())));
    }

    // 채널 필터 UI(칩) 옵션 목록용 - Platform.TELEGRAM으로 한정(VideoFeedService
    // .getChannels()가 Platform.YOUTUBE로 한정한 것과 대칭).
    @Transactional(readOnly = true)
    public List<TelegramFeedChannelResponse> getChannels() {
        return channelRepository.findByPlatformAndEnabledTrueOrderByPriorityAsc(Platform.TELEGRAM).stream()
            .map(TelegramFeedMapper::toFeedChannelResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public TelegramFeedDetailResponse getPostDetail(Long telegramPostId) {
        TelegramPost post = telegramPostRepository.findSummarizedPostById(telegramPostId)
            .orElseThrow(() -> new NotFoundException(TelegramFeedErrorCode.NOT_FOUND_POST));
        TelegramSummary summary = telegramSummaryRepository.findByTelegramPost(post)
            .orElseThrow(() -> new NotFoundException(TelegramFeedErrorCode.NOT_FOUND_POST));
        List<TelegramPostTicker> tickers = telegramPostTickerRepository.findByTelegramPost(post);
        return TelegramFeedMapper.toDetailResponse(post, toPayload(summary), tickers);
    }

    private Map<Long, String> toSummaryTextMap(List<TelegramSummary> summaries) {
        Map<Long, String> result = new HashMap<>();
        for (TelegramSummary summary : summaries) {
            result.put(summary.getTelegramPost().getId(), toPayload(summary).summary());
        }
        return result;
    }

    private Map<Long, List<TelegramPostTicker>> groupByPostId(List<TelegramPostTicker> tickers) {
        return tickers.stream().collect(Collectors.groupingBy(ticker -> ticker.getTelegramPost().getId()));
    }

    private TelegramSummaryPayload toPayload(TelegramSummary summary) {
        try {
            return objectMapper.readValue(summary.getPayload(), TelegramSummaryPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                "텔레그램 요약 payload 역직렬화에 실패했습니다: telegramSummaryId=" + summary.getId(), e);
        }
    }
}
