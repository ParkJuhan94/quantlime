package com.quantlime.telegramfeed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.infra.python.dto.SummarizeApiResponse;
import com.quantlime.stock.repository.StockRepository;
import com.quantlime.telegramfeed.domain.TelegramDigest;
import com.quantlime.telegramfeed.domain.TelegramDigestTicker;
import com.quantlime.telegramfeed.repository.TelegramDigestRepository;
import com.quantlime.telegramfeed.repository.TelegramDigestTickerRepository;
import com.quantlime.videofeed.domain.Channel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SummaryPersistService(유튜브)와 구조는 비슷하지만 대상이 글이 아니라
 * 채널×날짜 다이제스트라 findById 대신 upsert(findByChannelAndDigestDate 후
 * 있으면 덮어쓰기, 없으면 신규 생성)로 동작한다. 종목마스터에 없는 티커
 * 코드를 skip+log.warn하는 AI 환각 방어는 그대로 복제(텔레그램 채널이 해외
 * 종목 위주라 환각 위험이 더 크다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramDigestPersistService {

    private final TelegramDigestRepository telegramDigestRepository;
    private final TelegramDigestTickerRepository telegramDigestTickerRepository;
    private final StockRepository stockRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void persistResult(Channel channel, LocalDate digestDate, SummarizeApiResponse response) {
        String payload = toPayloadJson(response);
        TelegramDigest digest = telegramDigestRepository.findByChannelAndDigestDate(channel, digestDate)
            .map(existing -> {
                existing.overwrite(response.model(), payload, response.inputTokens(), response.outputTokens());
                return existing;
            })
            .orElseGet(() -> telegramDigestRepository.save(
                TelegramDigest.of(channel, digestDate, response.model(), payload,
                    response.inputTokens(), response.outputTokens())));

        // 재생성(수집 사이클마다 누적 재요약)마다 태깅 종목 집합이 바뀔 수 있어
        // 이전 태깅을 지우고 새로 채운다.
        telegramDigestTickerRepository.deleteByTelegramDigest(digest);
        for (SummarizeApiResponse.TickerMentionApiResponse ticker : response.mentionedTickers()) {
            if (!stockRepository.existsByStockCode(ticker.tickerCode())) {
                log.warn("AI가 태깅한 종목코드가 실제 종목마스터에 없어 건너뜀: "
                    + "channel={}, digestDate={}, tickerCode={}", channel.getName(), digestDate, ticker.tickerCode());
                continue;
            }
            telegramDigestTickerRepository.save(TelegramDigestTicker.of(
                digest, ticker.tickerCode(), ticker.tickerName(), ticker.stance(),
                BigDecimal.valueOf(ticker.confidence())));
        }
    }

    private String toPayloadJson(SummarizeApiResponse response) {
        // model/input_tokens/output_tokens는 TelegramDigest 엔티티의 별도 컬럼으로
        // 이미 저장되므로 payload에 중복해서 담지 않는다.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", response.summary());
        payload.put("key_points", response.keyPoints());
        payload.put("macro_points", response.macroPoints());
        payload.put("mentioned_tickers", response.mentionedTickers());
        payload.put("caveat", response.caveat());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("텔레그램 다이제스트 payload 직렬화에 실패했습니다.", e);
        }
    }
}
