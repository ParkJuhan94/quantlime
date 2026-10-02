package com.quantlime.price.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.price.dto.response.MinuteChartResponse;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 종목 분봉 차트 페이지를 Redis에 짧게 캐싱한다(2026-10-01 결정 - 분봉은 DB에 저장하지
 * 않고 보는 종목만 온디맨드로 토스를 호출하는 대신 이 캐시로 요청을 합친다). 같은
 * 종목을 여러 사용자가 동시에 보거나 새로고침해도 토스 {@code MARKET_DATA_CHART} 호출
 * 예산(일봉 배치와 공유)을 TTL당 1회만 쓴다.
 *
 * <p>커서 없이 "최신"을 요청한 페이지는 새 봉이 계속 생기므로 짧게, {@code before}로 과거를
 * 지정한 페이지는 불변이라 길게 캐싱한다. Redis 장애는 캐시 미스로 취급한다
 * ({@link PriceCacheStore}와 동일한 판단).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinuteChartCacheStore {

    private static final String KEY_PREFIX = "chart:1m:";
    static final Duration LATEST_TTL = Duration.ofSeconds(15);
    static final Duration HISTORY_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<MinuteChartResponse> find(String stockCode, String before) {
        try {
            String json = redisTemplate.opsForValue().get(key(stockCode, before));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, MinuteChartResponse.class));
        } catch (DataAccessException e) {
            log.warn("분봉 캐시 조회 실패(Redis 연결): stockCode={}, error={}", stockCode, e.getMessage());
            return Optional.empty();
        } catch (JsonProcessingException e) {
            log.warn("분봉 캐시 파싱 실패: stockCode={}, error={}", stockCode, e.getMessage());
            return Optional.empty();
        }
    }

    public void save(String stockCode, String before, MinuteChartResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key(stockCode, before), json, before == null ? LATEST_TTL : HISTORY_TTL);
        } catch (JsonProcessingException e) {
            log.warn("분봉 캐시 저장 실패(직렬화): stockCode={}, error={}", stockCode, e.getMessage(), e);
        } catch (DataAccessException e) {
            log.warn("분봉 캐시 저장 실패(Redis 연결): stockCode={}, error={}", stockCode, e.getMessage());
        }
    }

    private String key(String stockCode, String before) {
        return KEY_PREFIX + stockCode + ":" + (before == null ? "latest" : before);
    }
}
