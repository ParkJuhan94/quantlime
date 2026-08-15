package com.quantlime.telegramfeed.domain;

import com.quantlime.common.exception.ValidationException;
import com.quantlime.telegramfeed.exception.TelegramFeedErrorCode;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 전이: DISCOVERED -> FILTERED_OUT
 *              -> SELECTED
 *
 * 유튜브 VideoStatus와 달리 PENDING_REVIEW(velocity 판정 유예)가 없다 -
 * 그 단계는 순전히 조회수/경과시간 velocity 판정 때문에 존재하는데,
 * 텔레그램 하드필터(글자수/키워드)는 게시 즉시 확정된 값만 보므로 유예할
 * 이유가 없다.
 *
 * <p>2026-08-15부로 SUMMARIZED/FAILED를 제거했다 - 요약이 글 단위에서
 * 채널×날짜 단위 다이제스트(TelegramDigest)로 바뀌면서, "이 글이
 * 요약됐는지"는 더 이상 글 자체의 상태가 아니라 그 글이 속한 다이제스트가
 * 존재하는지로 판단한다. 재시도도 글 단위 retryCount가 아니라 다이제스트
 * 생성이 다음 스케줄에서 자연히 재시도되는 구조로 바뀌었다.
 */
@Getter
@RequiredArgsConstructor
public enum TelegramPostStatus {

    DISCOVERED("발견됨"),
    FILTERED_OUT("필터링됨"),
    SELECTED("선정됨");

    private final String label;

    public static TelegramPostStatus of(String label) {
        return Arrays.stream(values())
            .filter(status -> status.label.equals(label))
            .findFirst()
            .orElseThrow(() -> new ValidationException(TelegramFeedErrorCode.INVALID_POST_STATUS));
    }
}
