package com.quantlime.price.service;

import com.quantlime.price.domain.StockLiquidity;
import com.quantlime.price.dto.LiquiditySnapshot;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.repository.OverseasDailyPriceRepository;
import com.quantlime.price.repository.StockLiquidityRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종목별 유동성 스냅샷(최근 20거래일 일평균 거래대금·거래량 0인 날 수)을
 * 갱신한다 - {@link StockLiquidity} 클래스 주석 참고. 종목당 최신 1건만
 * 유지(upsert)하며, {@link com.quantlime.market.service.MarketDataRefreshService}가
 * 가격 gap-fill 직후·스코어 재계산 이전에 호출한다(2026-09 감사 세션).
 *
 * <p>{@code refreshDomestic}/{@code refreshOverseas}를 각각 {@code @Transactional}
 * public 메서드로 두고 upsert 루프는 그 안에서 private 헬퍼로 호출한다 -
 * 헬퍼 자체에 {@code @Transactional}을 붙여 두 곳에서 self-invocation으로
 * 부르면 프록시를 안 타 트랜잭션이 조용히 무시된다(VideoRetentionService
 * 등에서 이미 실제로 겪은 함정, 전역 Spring Boot 컨벤션 "리포지토리" 참고).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockLiquidityService {

    // 국내(원)/해외(달러)는 거래대금 통화 단위가 달라 컷 임계값을 따로
    // 둔다 - 절대 금액 기준(상위 N% 대신)인 이유는 시장 전체가 침체해도
    // 항상 같은 비율을 통과시키는 상대 기준과 달리, "실제로 거래되는
    // 종목"이라는 절대적 기준을 사용자가 원했기 때문(2026-09 감사 세션).
    // 국내 실측(2026-09) 기준 10억원 이상이면 약 35%(911/2594종목) 통과 -
    // 3~10억 구간(480종목)이 명백한 잡주 영역이었다.
    @Value("${score-liquidity.min-avg-trading-value-domestic:1000000000}")
    private double minAvgTradingValueDomestic;

    // 해외 실측 기준 100만 달러 이상이면 약 4,009종목 통과.
    @Value("${score-liquidity.min-avg-trading-value-overseas:1000000}")
    private double minAvgTradingValueOverseas;

    // 최근 20거래일 중 거래량 0인 날이 이 값 이상이면 거래정지로 간주해
    // 제외한다 - 국내 실측 기준 20일 내내 0인 종목이 79개 존재했다.
    @Value("${score-liquidity.max-zero-volume-days:5}")
    private int maxZeroVolumeDays;

    private final DomesticDailyPriceRepository domesticDailyPriceRepository;
    private final OverseasDailyPriceRepository overseasDailyPriceRepository;
    private final StockLiquidityRepository stockLiquidityRepository;

    @Transactional
    public void refreshDomestic(LocalDate since) {
        upsertSnapshots(domesticDailyPriceRepository.findLiquiditySnapshot(since), minAvgTradingValueDomestic);
    }

    @Transactional
    public void refreshOverseas(LocalDate since) {
        upsertSnapshots(overseasDailyPriceRepository.findLiquiditySnapshot(since), minAvgTradingValueOverseas);
    }

    private void upsertSnapshots(List<LiquiditySnapshot> snapshots, double minAvgTradingValue) {
        LocalDate asOf = LocalDate.now();
        for (LiquiditySnapshot snapshot : snapshots) {
            int zeroVolumeDays = snapshot.zeroVolumeDays() == null ? 0 : snapshot.zeroVolumeDays().intValue();
            boolean liquid = snapshot.avgTradingValue() != null
                && snapshot.avgTradingValue() >= minAvgTradingValue
                && zeroVolumeDays < maxZeroVolumeDays;
            stockLiquidityRepository.findByStockCode(snapshot.stockCode())
                .ifPresentOrElse(
                    existing -> existing.updateFrom(asOf, snapshot.avgTradingValue(), zeroVolumeDays, liquid),
                    () -> stockLiquidityRepository.save(StockLiquidity.of(
                        snapshot.stockCode(), asOf, snapshot.avgTradingValue(), zeroVolumeDays, liquid)));
        }
        log.info("유동성 스냅샷 갱신 완료: 종목수={}", snapshots.size());
    }
}
