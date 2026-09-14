package com.quantlime.videofeed.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * IP 차단으로 운영 서버에서 직접 자막을 못 가져올 때(2026-09 발견,
 * youtube-transcript-api가 AWS IP를 차단), 로컬에서 미리 수집해둔 자막을
 * 운영 DB에 반영하기 위한 수동 임포트 요청. 항목 하나가 영상 한 건에
 * 대응하며, {@code externalVideoId}로 운영 DB의 Video를 찾아 매칭한다
 * (로컬/운영이 서로 다른 내부 PK를 쓰므로 이 외부 ID가 유일한 매칭 키).
 */
public record TranscriptImportRequest(
    @NotEmpty(message = "가져올 자막 목록은 비어있을 수 없습니다.")
    @Valid
    List<Item> items
) {

    public record Item(
        @NotBlank(message = "externalVideoId는 필수입니다.")
        String externalVideoId,

        @NotBlank(message = "source는 필수입니다.")
        String source,

        @NotBlank(message = "lang은 필수입니다.")
        String lang,

        @NotBlank(message = "content는 필수입니다.")
        String content,

        @NotNull(message = "charCount는 필수입니다.")
        @PositiveOrZero(message = "charCount는 0 이상이어야 합니다.")
        Integer charCount
    ) {
    }
}
