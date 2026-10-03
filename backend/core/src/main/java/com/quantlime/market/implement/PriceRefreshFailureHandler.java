package com.quantlime.market.implement;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.score.domain.PeerGroup;
import com.quantlime.stock.service.StockMasterService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * 종목별 가격 갱신 실패의 분류와 후처리를 맡는 구현 레이어(Implementation) -
 * {@code MarketDataRefreshService}가 예외 타입 판별·미커버 종목 표시·실패 메트릭을
 * 직접 다루지 않고 "이 실패를 어떻게 처리할지"만 이 컴포넌트에 위임한다.
 *
 * <p>실패는 여기서 삼켜진다(컨슈머는 성공으로 처리하고 {@code @RetryableTopic}/DLT에도
 * 잡히지 않는다) - 규모는 {@code market.price.refresh.failures} 카운터로 본다
 * (2026-10-01, Kafka 점검).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceRefreshFailureHandler {

    private final StockMasterService stockMasterService;
    private final MeterRegistry meterRegistry;

    /**
     * 국내 가격 갱신 실패를 처리한다. Toss 캔들 API가 커버하지 않는 종목
     * (KONEX·스팩·상폐 잔존 등)은 조회 시 stock-not-found(404)만 반복하므로,
     * 이 경우 해당 종목을 '가격 미커버'로 표시해 이후 기동의 갭필 및 랭킹
     * 스윕(DomesticListedStockCache) 대상에서 제외한다 - 매 기동 404 폭주와
     * 불필요한 Toss 쿼터 소모를 근본 차단한다. 그 외 실패(레이트리밋·일시
     * 장애 등)는 다음 기동에 재시도해야 하므로 표시하지 않고 에러 로그만 남긴다.
     */
    public void handleDomestic(String stockCode, Exception e) {
        if (isStockNotFound(e)) {
            stockMasterService.markPriceUnsupported(stockCode);
            log.info("Toss 미커버 종목(stock-not-found)으로 표시, 이후 갭필/스윕에서 제외: stockCode={}", stockCode);
            return;
        }
        recordFailure(PeerGroup.DOMESTIC);
        // 레이트리밋/일시 장애 등으로 다수 종목이 한꺼번에 실패하면(예: Toss
        // 장애) 종목마다 풀 스택트레이스를 찍는 게 콘솔을 뒤덮어 정작 원인
        // 파악을 방해한다. 원인 자체(스택트레이스)가 필요하면 재현 후
        // debug 레벨로 임시 확인할 것.
        log.warn("국내 가격 갱신 실패(해당 종목만 스킵): stockCode={}, error={}", stockCode, e.getMessage());
        log.debug("국내 가격 갱신 실패 상세: stockCode={}", stockCode, e);
    }

    /**
     * 해외 가격 갱신 실패를 처리한다. 국내({@link #handleDomestic})와 대칭 -
     * 해외도 이제 같은 Toss 캔들 API를 쓰므로(2026-07-29, KIS에서 이관)
     * stock-not-found 판별 로직을 그대로 공유한다. 이 안전장치가 없던 이전
     * 버전에서는 KIS 전용 마스터에만 있고 실제로는 조회 불가능한 종목이
     * 매 스윕마다 계속 실패하면서도 영원히 제외되지 않아, 레이트리밋 예산을
     * 갉아먹으며 다른 정상 종목의 산발적 실패(레이트리밋)를 유발하는 원인
     * 중 하나였다.
     *
     * <p>해외는 여기에 더해 {@code isUnsupportedSymbolFormat}도 함께 본다 -
     * KIS 해외주식 마스터파일에서 유래한 종목코드 중 "AAC/UN"·"ABR/F"처럼
     * "/"가 섞인 SPAC 유닛/우선주 표기가 있는데(길이 6자 제한만으로는 안
     * 걸러짐, OverseasStockMasterSyncService 참고), Toss 심볼 파라미터는
     * `^[A-Za-z0-9.,\-]+$`만 허용해 이런 종목은 항상 404가 아니라 400으로
     * 거부된다(실측 - 2026-07-30). 404만 보던 기존 체크로는 이 400이
     * 잡히지 않아 매 기동 무한 반복 실패의 원인이 됐다.
     */
    public void handleOverseas(String stockCode, Exception e) {
        if (isStockNotFound(e) || isUnsupportedSymbolFormat(e)) {
            stockMasterService.markPriceUnsupported(stockCode);
            log.info("Toss 미커버 해외종목(stock-not-found/invalid-symbol)으로 표시, 이후 갭필/스윕에서 제외: stockCode={}", stockCode);
            return;
        }
        recordFailure(PeerGroup.OVERSEAS);
        log.warn("해외 가격 갱신 실패(해당 종목만 스킵): stockCode={}, error={}", stockCode, e.getMessage());
        log.debug("해외 가격 갱신 실패 상세: stockCode={}", stockCode, e);
    }

    /**
     * Toss 캔들/현재가 API는 심볼이 자신의 문자 패턴(`^[A-Za-z0-9.,\-]+$`)을
     * 벗어나면 400 Bad Request로 거부한다 - 우리 쪽 요청 파라미터 자체는
     * 항상 올바르게 구성되므로(stockCode는 DB에 이미 저장된 값을 그대로
     * 전달), 이 400은 사실상 항상 "이 심볼은 Toss가 절대 못 받는다"는
     * 영구적 신호다(일시적 장애가 아님).
     */
    private boolean isUnsupportedSymbolFormat(Exception e) {
        return e instanceof ExternalApiException
            && e.getCause() instanceof HttpClientErrorException.BadRequest;
    }

    private boolean isStockNotFound(Exception e) {
        return e instanceof ExternalApiException
            && e.getCause() instanceof HttpClientErrorException.NotFound;
    }

    private void recordFailure(PeerGroup peerGroup) {
        Counter.builder("market.price.refresh.failures")
            .tag("peerGroup", peerGroup.getWireValue())
            .description("가격 갱신 중 삼켜진(재시도/DLT로 가지 않는) 외부 API 실패 수")
            .register(meterRegistry)
            .increment();
    }
}
