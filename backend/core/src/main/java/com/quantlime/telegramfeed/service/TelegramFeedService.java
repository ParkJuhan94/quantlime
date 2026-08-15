package com.quantlime.telegramfeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.telegramfeed.domain.TelegramDigest;
import com.quantlime.telegramfeed.domain.TelegramDigestTicker;
import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostStatus;
import com.quantlime.telegramfeed.dto.TelegramSummaryPayload;
import com.quantlime.telegramfeed.dto.mapper.TelegramFeedMapper;
import com.quantlime.telegramfeed.dto.response.TelegramFeedChannelResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedDigestDetailResponse;
import com.quantlime.telegramfeed.dto.response.TelegramFeedDigestResponse;
import com.quantlime.telegramfeed.exception.TelegramFeedErrorCode;
import com.quantlime.telegramfeed.repository.TelegramDigestRepository;
import com.quantlime.telegramfeed.repository.TelegramDigestTickerRepository;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.videofeed.domain.Platform;
import com.quantlime.videofeed.repository.ChannelRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// VideoFeedService(유튜브)와 구조적으로 동일하되 대상이 글이 아니라 채널×날짜
// 다이제스트다(2026-08-15 재설계). 공개 조회 전용, 관리자 정보는 노출하지 않는다.
@Service
@RequiredArgsConstructor
public class TelegramFeedService {

    private final TelegramDigestRepository telegramDigestRepository;
    private final TelegramDigestTickerRepository telegramDigestTickerRepository;
    private final TelegramPostRepository telegramPostRepository;
    private final ChannelRepository channelRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Slice<TelegramFeedDigestResponse> getDigests(String tickerCode, Long channelId, LocalDate date, Pageable pageable) {
        Slice<TelegramDigest> digests = telegramDigestRepository.findDigests(tickerCode, channelId, date, pageable);

        List<Long> digestIds = digests.getContent().stream().map(TelegramDigest::getId).toList();
        Map<Long, String> summaryByDigestId = toSummaryTextMap(digests.getContent());
        Map<Long, List<TelegramDigestTicker>> tickersByDigestId =
            groupByDigestId(telegramDigestTickerRepository.findByTelegramDigest_IdIn(digestIds));

        return digests.map(digest -> TelegramFeedMapper.toDigestResponse(
            digest,
            summaryByDigestId.getOrDefault(digest.getId(), ""),
            tickersByDigestId.getOrDefault(digest.getId(), List.of()),
            countSourcePosts(digest)));
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
    public TelegramFeedDigestDetailResponse getDigestDetail(Long telegramDigestId) {
        TelegramDigest digest = telegramDigestRepository.findByIdWithChannel(telegramDigestId)
            .orElseThrow(() -> new NotFoundException(TelegramFeedErrorCode.NOT_FOUND_POST));
        List<TelegramDigestTicker> tickers = telegramDigestTickerRepository.findByTelegramDigest(digest);
        List<String> sourcePostUrls = findSourcePosts(digest).stream()
            .map(this::toPostUrl)
            .toList();
        return TelegramFeedMapper.toDigestDetailResponse(digest, toPayload(digest), tickers, sourcePostUrls);
    }

    private int countSourcePosts(TelegramDigest digest) {
        return findSourcePosts(digest).size();
    }

    private List<TelegramPost> findSourcePosts(TelegramDigest digest) {
        LocalDate date = digest.getDigestDate();
        return telegramPostRepository.findByChannelAndStatusAndPublishedAtBetween(
                digest.getChannel(), TelegramPostStatus.SELECTED, date.atStartOfDay(), date.plusDays(1).atStartOfDay())
            .stream()
            .sorted(Comparator.comparing(TelegramPost::getPublishedAt))
            .toList();
    }

    private String toPostUrl(TelegramPost post) {
        return "https://t.me/" + post.getChannel().getExternalChannelId() + "/" + post.getMessageId();
    }

    private Map<Long, String> toSummaryTextMap(List<TelegramDigest> digests) {
        Map<Long, String> result = new HashMap<>();
        for (TelegramDigest digest : digests) {
            result.put(digest.getId(), toPayload(digest).summary());
        }
        return result;
    }

    private Map<Long, List<TelegramDigestTicker>> groupByDigestId(List<TelegramDigestTicker> tickers) {
        return tickers.stream().collect(Collectors.groupingBy(ticker -> ticker.getTelegramDigest().getId()));
    }

    private TelegramSummaryPayload toPayload(TelegramDigest digest) {
        try {
            return objectMapper.readValue(digest.getPayload(), TelegramSummaryPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                "텔레그램 다이제스트 payload 역직렬화에 실패했습니다: telegramDigestId=" + digest.getId(), e);
        }
    }
}
