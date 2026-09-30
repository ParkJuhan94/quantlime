package com.quantlime.infra.sync;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quantlime.videofeed.dto.request.TranscriptImportRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Tag("unit")
class SyncApiClientTest {

    private static final String BASE_URL = "https://prod.test";

    private MockRestServiceServer mockServer;
    private SyncApiClient client;

    private final TranscriptImportRequest.Item item =
        new TranscriptImportRequest.Item("vid1", "youtube", "ko", "자막 본문", 5);

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new SyncApiClient(builder.build(), new SyncProperties("sync-secret", BASE_URL));
    }

    @Test
    @DisplayName("[자막 1건을 items 배열로 감싸 X-Sync-Api-Key 헤더와 함께 운영 import 경로로 POST한다]")
    void pushTranscript_postsWithApiKeyHeader() {
        // given
        mockServer.expect(requestTo(BASE_URL + "/api/admin/feed/transcripts/import"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Sync-Api-Key", "sync-secret"))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].externalVideoId").value("vid1"))
            .andExpect(jsonPath("$.items[0].charCount").value(5))
            .andRespond(withSuccess());

        // when
        client.pushTranscript(item);

        // then
        mockServer.verify();
    }

    @Test
    @DisplayName("[운영 서버 오류는 호출측(LocalTranscriptSyncService)이 재시도하도록 예외를 그대로 전파한다]")
    void pushTranscript_serverError_propagates() {
        mockServer.expect(requestTo(BASE_URL + "/api/admin/feed/transcripts/import")).andRespond(withServerError());

        assertThatThrownBy(() -> client.pushTranscript(item)).isInstanceOf(RestClientResponseException.class);
    }
}
