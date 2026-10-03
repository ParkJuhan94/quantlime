package com.quantlime.infra.youtube;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.youtube.dto.YoutubePlaylistItemsResponse;
import com.quantlime.infra.youtube.dto.YoutubeVideosResponse;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@Tag("unit")
class YoutubeApiClientTest {

    private static final String BASE_URL = "https://yt.test";

    private MockRestServiceServer mockServer;
    private YoutubeApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new YoutubeApiClient(builder.build(), new YoutubeApiProperties("api-key", BASE_URL));
    }

    @Test
    @DisplayName("[플레이리스트 조회는 API 키/50개 상한을 실어 호출하고, 첫 페이지엔 pageToken을 붙이지 않는다]")
    void getPlaylistItems_firstPage_noPageToken() {
        // given
        mockServer.expect(requestTo(Matchers.startsWith(BASE_URL + "/playlistItems")))
            .andExpect(queryParam("part", "snippet"))
            .andExpect(queryParam("playlistId", "UU123"))
            .andExpect(queryParam("maxResults", "50"))
            .andExpect(queryParam("key", "api-key"))
            .andExpect(request -> assertThat(request.getURI().getQuery()).doesNotContain("pageToken"))
            .andRespond(withSuccess("""
                {"nextPageToken":"NEXT","items":[{"snippet":{"title":"영상","publishedAt":"2026-09-30T00:00:00Z",
                "resourceId":{"videoId":"vid1"}}}]}
                """, MediaType.APPLICATION_JSON));

        // when
        YoutubePlaylistItemsResponse response = client.getPlaylistItems("UU123", null);

        // then
        assertThat(response.nextPageToken()).isEqualTo("NEXT");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).snippet().resourceId().videoId()).isEqualTo("vid1");
        mockServer.verify();
    }

    @Test
    @DisplayName("[다음 페이지 조회는 pageToken을 쿼리에 붙인다]")
    void getPlaylistItems_nextPage_sendsPageToken() {
        mockServer.expect(requestTo(Matchers.startsWith(BASE_URL + "/playlistItems")))
            .andExpect(queryParam("pageToken", "NEXT"))
            .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.getPlaylistItems("UU123", "NEXT").items()).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("[영상 상세 조회는 ID를 콤마로 합쳐 한 번에 호출한다]")
    void getVideos_joinsIds() {
        mockServer.expect(requestTo(Matchers.startsWith(BASE_URL + "/videos")))
            .andExpect(queryParam("part", "contentDetails,statistics"))
            .andExpect(queryParam("id", "a,b"))
            .andRespond(withSuccess("""
                {"items":[{"id":"a","contentDetails":{"duration":"PT1M"},"statistics":{"viewCount":"10"}}]}
                """, MediaType.APPLICATION_JSON));

        YoutubeVideosResponse response = client.getVideos(List.of("a", "b"));

        assertThat(response.items().get(0).contentDetails().duration()).isEqualTo("PT1M");
        assertThat(response.items().get(0).statistics().viewCount()).isEqualTo("10");
        mockServer.verify();
    }

    @Test
    @DisplayName("[403(쿼터 초과)는 QUOTA_EXCEEDED(YT_002)로 매핑된다]")
    void getPlaylistItems_forbidden_mapsToQuotaExceeded() {
        mockServer.expect(requestTo(Matchers.startsWith(BASE_URL + "/playlistItems")))
            .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.getPlaylistItems("UU123", null))
            .isInstanceOfSatisfying(ExternalApiException.class, e -> assertThat(e.getCode()).isEqualTo("YT_002"));
    }

    @Test
    @DisplayName("[403 외 오류는 조회 실패 코드(YT_001)로 감싼다]")
    void getVideos_serverError_mapsToInquiryFailed() {
        mockServer.expect(requestTo(Matchers.startsWith(BASE_URL + "/videos")))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getVideos(List.of("a")))
            .isInstanceOfSatisfying(ExternalApiException.class, e -> assertThat(e.getCode()).isEqualTo("YT_001"));
    }

    @Test
    @DisplayName("[채널 조회도 ID를 콤마로 합치고 쿼터 초과를 YT_002로 매핑한다]")
    void getChannels_forbidden_mapsToQuotaExceeded() {
        mockServer.expect(requestTo(Matchers.startsWith(BASE_URL + "/channels")))
            .andExpect(queryParam("id", "c1,c2"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.getChannels(List.of("c1", "c2")))
            .isInstanceOfSatisfying(ExternalApiException.class, e -> assertThat(e.getCode()).isEqualTo("YT_002"));
    }

    @Test
    @DisplayName("[채널 조회 응답 본문이 비면 YT_003으로 실패한다]")
    void getChannels_emptyBody_mapsToChannelsFailed() {
        mockServer.expect(requestTo(Matchers.startsWith(BASE_URL + "/channels"))).andRespond(withSuccess());

        assertThatThrownBy(() -> client.getChannels(List.of("c1")))
            .isInstanceOfSatisfying(ExternalApiException.class, e -> assertThat(e.getCode()).isEqualTo("YT_003"));
    }
}
