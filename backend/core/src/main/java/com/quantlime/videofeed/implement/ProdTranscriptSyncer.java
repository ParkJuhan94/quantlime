package com.quantlime.videofeed.implement;

import com.quantlime.infra.sync.SyncApiClient;
import com.quantlime.infra.sync.SyncProperties;
import com.quantlime.videofeed.domain.Transcript;
import com.quantlime.videofeed.domain.Video;
import com.quantlime.videofeed.dto.request.TranscriptImportRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 로컬에서 만든 자막을 운영 서버로 밀어넣는 구현 레이어(Implementation) - 동기화 설정 여부
 * 판단과 운영 API 호출을 이 컴포넌트가 맡는다. 실패 처리(예외를 삼키고 로그만 남김)는
 * 호출부 정책이다.
 */
@Component
@RequiredArgsConstructor
public class ProdTranscriptSyncer {

    private final SyncApiClient syncApiClient;
    private final SyncProperties syncProperties;

    /** 운영 동기화 키와 운영 API 주소가 모두 설정됐는지. */
    public boolean isConfigured() {
        return StringUtils.hasText(syncProperties.getApiKey())
            && StringUtils.hasText(syncProperties.getProdApiBase());
    }

    public void push(Video video, Transcript transcript) {
        syncApiClient.pushTranscript(new TranscriptImportRequest.Item(
            video.getExternalVideoId(), transcript.getSource(), transcript.getLang(),
            transcript.getContent(), transcript.getCharCount()));
    }
}
