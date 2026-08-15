package com.quantlime.telegramfeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.common.exception.NotFoundException;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.stock.repository.StockRepository;
import com.quantlime.telegramfeed.domain.TelegramPost;
import com.quantlime.telegramfeed.domain.TelegramPostTicker;
import com.quantlime.telegramfeed.domain.TelegramSummary;
import com.quantlime.telegramfeed.exception.TelegramFeedErrorCode;
import com.quantlime.telegramfeed.repository.TelegramPostRepository;
import com.quantlime.telegramfeed.repository.TelegramPostTickerRepository;
import com.quantlime.telegramfeed.repository.TelegramSummaryRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SummaryPersistService(유튜브)와 구조적으로 동일 - AI 요약 결과(외부 I/O)와
 * 영속화를 분리한 짧은 트랜잭션 계층. 종목마스터에 없는 티커 코드를
 * skip+log.warn하는 AI 환각 방어도 그대로 복제한다(텔레그램 채널이 해외 종목
 * 위주라 환각 위험이 더 크다 - docs/ROADMAP.md "Phase 8 P7" 참고).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramSummaryPersistService {

    private final TelegramPostRepository telegramPostRepository;
    private final TelegramSummaryRepository telegramSummaryRepository;
    private final TelegramPostTickerRepository telegramPostTickerRepository;
    private final StockRepository stockRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void persistResult(Long telegramPostId, SummarizeApiResponse response) {
        TelegramPost post = findPost(telegramPostId);

        telegramSummaryRepository.save(TelegramSummary.of(
            post, response.model(), toPayloadJson(response),
            response.inputTokens(), response.outputTokens()));

        for (SummarizeApiResponse.TickerMentionApiResponse ticker : response.mentionedTickers()) {
            if (!stockRepository.existsByStockCode(ticker.tickerCode())) {
                log.warn("AI가 태깅한 종목코드가 실제 종목마스터에 없어 건너뜀: "
                    + "telegramPostId={}, tickerCode={}", telegramPostId, ticker.tickerCode());
                continue;
            }
            telegramPostTickerRepository.save(TelegramPostTicker.of(
                post, ticker.tickerCode(), ticker.tickerName(), ticker.stance(),
                BigDecimal.valueOf(ticker.confidence())));
        }

        post.markSummarized();
    }

    @Transactional
    public void markSummarizeFailed(Long telegramPostId, String reason) {
        findPost(telegramPostId).markFailed(reason);
    }

    private TelegramPost findPost(Long telegramPostId) {
        return telegramPostRepository.findById(telegramPostId)
            .orElseThrow(() -> new NotFoundException(TelegramFeedErrorCode.NOT_FOUND_POST));
    }

    private String toPayloadJson(SummarizeApiResponse response) {
        // model/input_tokens/output_tokens는 TelegramSummary 엔티티의 별도 컬럼으로
        // 이미 저장되므로 payload에 중복해서 담지 않는다(SummaryPersistService와 동일).
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", response.summary());
        payload.put("key_points", response.keyPoints());
        payload.put("macro_points", response.macroPoints());
        payload.put("mentioned_tickers", response.mentionedTickers());
        payload.put("caveat", response.caveat());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("텔레그램 요약 payload 직렬화에 실패했습니다.", e);
        }
    }
}
