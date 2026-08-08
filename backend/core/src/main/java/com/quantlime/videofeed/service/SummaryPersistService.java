package com.quantlime.videofeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.stock.repository.StockRepository;
import com.quantlime.videofeed.domain.Summary;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.domain.VideoTicker;
import com.quantlime.videofeed.exception.VideoFeedErrorCode;
import com.quantlime.videofeed.repository.SummaryRepository;
import com.quantlime.videofeed.repository.VideoRepository;
import com.quantlime.videofeed.repository.VideoTickerRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 요약 결과(외부 I/O)와 영속화를 분리한 짧은 트랜잭션 계층
 * (TranscriptPersistService와 동일한 이유로 호출부인
 * SummaryCollectionFacade와 별도 빈으로 둔다 - self-invocation
 * @Transactional 우회 버그 재발 방지, docs/CHANGELOG.md 2026-07-27 참고).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryPersistService {

    private final VideoRepository videoRepository;
    private final SummaryRepository summaryRepository;
    private final VideoTickerRepository videoTickerRepository;
    private final StockRepository stockRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void persistResult(Long videoId, SummarizeApiResponse response) {
        Video video = findVideo(videoId);

        summaryRepository.save(Summary.of(
            video, response.model(), toPayloadJson(response),
            response.inputTokens(), response.outputTokens()));

        for (SummarizeApiResponse.TickerMentionApiResponse ticker : response.mentionedTickers()) {
            if (!stockRepository.existsByStockCode(ticker.tickerCode())) {
                log.warn("AI가 태깅한 종목코드가 실제 종목마스터에 없어 건너뜀: "
                    + "videoId={}, tickerCode={}", videoId, ticker.tickerCode());
                continue;
            }
            videoTickerRepository.save(VideoTicker.of(
                video, ticker.tickerCode(), ticker.tickerName(), ticker.stance(),
                BigDecimal.valueOf(ticker.confidence())));
        }

        video.markSummarized();
    }

    @Transactional
    public void markSummarizeFailed(Long videoId, String reason) {
        findVideo(videoId).markFailed(reason);
    }

    private Video findVideo(Long videoId) {
        return videoRepository.findById(videoId)
            .orElseThrow(() -> new NotFoundException(VideoFeedErrorCode.NOT_FOUND_VIDEO));
    }

    private String toPayloadJson(SummarizeApiResponse response) {
        // model/input_tokens/output_tokens는 Summary 엔티티의 별도 컬럼으로 이미
        // 저장되므로 payload에 중복해서 담지 않는다.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", response.summary());
        payload.put("key_points", response.keyPoints());
        payload.put("macro_points", response.macroPoints());
        payload.put("mentioned_tickers", response.mentionedTickers());
        payload.put("caveat", response.caveat());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("요약 payload 직렬화에 실패했습니다.", e);
        }
    }
}
