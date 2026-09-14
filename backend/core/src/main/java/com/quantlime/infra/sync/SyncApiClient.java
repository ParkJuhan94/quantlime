package com.quantlime.infra.sync;

import com.quantlime.videofeed.dto.request.TranscriptImportRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 로컬에서 운영 서버로 자막을 실시간 전송한다(LocalTranscriptSyncService
 * 전용 - 이 프로젝트에서 유일하게 "우리 서버가 우리 서버를 호출하는"
 * 외부 API 클라이언트). 인증은 사용자 JWT가 아니라 대칭키(X-Sync-Api-Key,
 * SyncProperties)로 한다 - 사람이 브라우저에서 로그인해 토큰을 복사해오는
 * 수동 단계 없이도 안정적으로 반복 호출 가능해야 하기 때문.
 */
@Component
@RequiredArgsConstructor
public class SyncApiClient {

    private static final String API_KEY_HEADER = "X-Sync-Api-Key";
    private static final String IMPORT_PATH = "/api/admin/feed/transcripts/import";

    private final RestClient syncRestClient;
    private final SyncProperties properties;

    public void pushTranscript(TranscriptImportRequest.Item item) {
        syncRestClient.post()
            .uri(IMPORT_PATH)
            .header(API_KEY_HEADER, properties.getApiKey())
            .body(new TranscriptImportRequest(List.of(item)))
            .retrieve()
            .toBodilessEntity();
    }
}
