package com.quantlime.infra.kis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * GET /uapi/overseas-price/v1/quotations/dailyprice (tr_id=HHDFS76240000)
 * 응답. 2026-07-16 실제 앱키로 라이브 호출(NAS/AAPL)해 rt_cd=0 정상 응답과
 * 아래 필드 전부(xymd/clos/open/high/low/tvol) 확인 완료. 백테스트에
 * 필요한 최소 필드(거래일/OHLC/거래량)만 모델링한다.
 *
 * <p>output1.nrec로 호출당 최대 100건까지 옴을 확인(실제 응답 기준) -
 * Toss 국내 캔들의 count/before 커서 페이지네이션과 달리 KIS는 BYMD를
 * 갱신하며 페이지를 넘겨야 한다(Phase C 대량 백필 구현 시 참고).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KisOverseasDailyPriceResponse(
    @JsonProperty("rt_cd") String rtCd,
    String msg1,
    List<Candle> output2
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candle(
        String xymd,
        String clos,
        String open,
        String high,
        String low,
        String tvol
    ) {
    }
}
