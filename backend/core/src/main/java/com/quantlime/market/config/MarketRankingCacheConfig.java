package com.quantlime.market.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.market.cache.MarketRankingCache;
import com.quantlime.market.cache.TossMarketRankingCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link MarketRankingCache}는 생성자로 시장 범위(scope)를 주입받는
 * 구조라({@code WatchlistedStockCodeCache}/{@code PriceCacheConfig}와
 * 동일한 패턴) 컴포넌트 스캔으로 자동 등록될 수 없다 - 국내/해외 인스턴스를
 * 여기서 Bean으로 명시 등록한다. scope 문자열은 {@link TossMarketRankingCache}가
 * 이미 쓰는 "domestic"/"overseas" 상수를 그대로 재사용한다(Redis 키
 * 접미사와 Toss 랭킹 scope 쿼리 파라미터가 같은 값이어도 무방하고, 새
 * 상수를 추가로 만들 이유가 없다).
 */
@Configuration
public class MarketRankingCacheConfig {

    @Bean
    public MarketRankingCache domesticMarketRankingCache(
            StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new MarketRankingCache(TossMarketRankingCache.SCOPE_DOMESTIC, redisTemplate, objectMapper);
    }

    @Bean
    public MarketRankingCache overseasMarketRankingCache(
            StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new MarketRankingCache(TossMarketRankingCache.SCOPE_OVERSEAS, redisTemplate, objectMapper);
    }
}
