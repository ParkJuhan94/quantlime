package com.quantlime.telegramfeed.domain;

import com.quantlime.common.exception.ValidationException;
import com.quantlime.telegramfeed.exception.TelegramFeedErrorCode;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 전이: DISCOVERED -> FILTERED_OUT
 *              -> SELECTED -> SUMMARIZED
 *                          -> FAILED (retryCount++)
 *
 * 유튜브 VideoStatus와 달리 PENDING_REVIEW(velocity 판정 유예)가 없다 -
 * 그 단계는 순전히 조회수/경과시간 velocity 판정 때문에 존재하는데,
 * 텔레그램 하드필터(글자수/키워드)는 게시 즉시 확정된 값만 보므로 유예할
 * 이유가 없다. "게시 직후라 조회수가 불안정"한 문제는 하루 쿼터 랭킹
 * 기준을 viewCount 대신 charCount로 쓰는 것으로 대체했다(설계 근거는
 * docs/ROADMAP.md "Phase 8 P7" 참고).
 */
@Getter
@RequiredArgsConstructor
public enum TelegramPostStatus {

    DISCOVERED("발견됨"),
    FILTERED_OUT("필터링됨"),
    SELECTED("선정됨"),
    SUMMARIZED("요약 완료"),
    FAILED("실패");

    private final String label;

    public static TelegramPostStatus of(String label) {
        return Arrays.stream(values())
            .filter(status -> status.label.equals(label))
            .findFirst()
            .orElseThrow(() -> new ValidationException(TelegramFeedErrorCode.INVALID_POST_STATUS));
    }
}
