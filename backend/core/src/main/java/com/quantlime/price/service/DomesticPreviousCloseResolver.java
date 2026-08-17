package com.quantlime.price.service;

import com.quantlime.price.domain.DomesticDailyPrice;
import com.quantlime.price.domain.DomesticRegularClosePrice;
import com.quantlime.price.repository.DomesticDailyPriceRepository;
import com.quantlime.price.repository.DomesticRegularClosePriceRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 국내 종목의 "전일종가" 기준값을 정규장(15:30) 종가 우선으로 구한다.
 * {@code domestic_daily_price.close_price}는 NXT 프리/애프터마켓까지 반영해
 * 20:00까지 계속 갱신되는 값이라({@link DomesticRegularClosePrice} 클래스
 * 주석 참고), NXT로 거래되는 종목의 전일종가로 그대로 쓰면 등락률이 정규장
 * 기준(다른 증권사 앱이 쓰는 관례)과 어긋난다(2026-08-17 발견).
 *
 * <p>{@link DomesticRegularClosePrice}는 {@code
 * DomesticRegularCloseCaptureScheduler}가 매일 15:30에만 채우므로, 그 캡처가
 * 없었던 종목(이 기능 도입 이전 과거 날짜, 캡처 시점에 서버가 다운돼 있던 날
 * 등)은 이 값이 없다 - 그런 종목만 기존처럼 {@code domestic_daily_price}의
 * 확정 종가로 폴백한다. NXT 종목이 폴백을 타면 그날 하루는 등락률이 여전히
 * NXT 반영 기준일 수 있지만, 값 자체가 없는 것보다는 낫다({@code
 * StockPriceService}/{@code PreviousCloseCache}가 이 폴백값도 못 구하면 등락률
 * 자체를 표시하지 않는 것과 비교).
 */
@Component
@RequiredArgsConstructor
public class DomesticPreviousCloseResolver {

    private final DomesticRegularClosePriceRepository domesticRegularClosePriceRepository;
    private final DomesticDailyPriceRepository domesticDailyPriceRepository;

    public Map<String, Double> resolve(List<String> stockCodes, LocalDate date) {
        if (stockCodes.isEmpty()) {
            return Map.of();
        }

        Map<String, Double> fromRegularClose = domesticRegularClosePriceRepository
            .findLatestBeforeDate(stockCodes, date).stream()
            .collect(Collectors.toMap(
                DomesticRegularClosePrice::getStockCode,
                price -> price.getClosePrice().doubleValue()));

        List<String> missing = stockCodes.stream()
            .filter(code -> !fromRegularClose.containsKey(code))
            .toList();
        if (missing.isEmpty()) {
            return fromRegularClose;
        }

        Map<String, Double> fromDailyCandle = domesticDailyPriceRepository
            .findLatestBeforeDate(missing, date).stream()
            .collect(Collectors.toMap(
                DomesticDailyPrice::getStockCode,
                price -> price.getClosePrice().doubleValue()));

        Map<String, Double> merged = new HashMap<>(fromRegularClose);
        merged.putAll(fromDailyCandle);
        return merged;
    }
}
