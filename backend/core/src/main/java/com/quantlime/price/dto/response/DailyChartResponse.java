package com.quantlime.price.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;

// open/high/low/close는 Double이다 - 국내(원화)는 정수값이지만 해외(달러)는
// 소수점 단위(예: $317.31)라 국내/해외 공용 응답 타입을 Long으로 두면 해외
// 가격의 소수점이 잘린다(OverseasDailyPrice가 별도 엔티티로 분리된 것과
// 동일한 이유).
public record DailyChartResponse(
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "Asia/Seoul") LocalDate tradeDate,
    Double open,
    Double high,
    Double low,
    Double close,
    Long volume
) {
}
