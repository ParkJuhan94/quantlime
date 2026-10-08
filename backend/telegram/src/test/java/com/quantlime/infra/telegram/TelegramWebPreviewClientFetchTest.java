package com.quantlime.infra.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.quantlime.common.exception.ExternalApiException;
import com.quantlime.infra.telegram.dto.TelegramPreviewPage;
import com.quantlime.infra.telegram.exception.TelegramApiErrorCode;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 파서 자체는 {@link TelegramWebPreviewClientTest}(실제 캡처 HTML)가 맡고, 여기서는 HTTP 호출 조립(커서 쿼리)과
 * 실패 매핑, 조회수 표기 변환을 검증한다.
 */
@Tag("unit")
class TelegramWebPreviewClientFetchTest {

    private static final String BASE_URL = "https://t.test";
    // text/html은 charset 미지정 시 ISO-8859-1로 읽혀 한글이 깨진다(실서버는 UTF-8을 명시한다)
    private static final MediaType HTML_UTF8 = MediaType.parseMediaType("text/html;charset=UTF-8");

    private MockRestServiceServer server;
    private TelegramWebPreviewClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TelegramWebPreviewClient(builder.build());
    }

    private static String message(long id, String views) {
        return """
            <div class="tgme_widget_message" data-post="chan/%d">
              <a class="tgme_widget_message_date"><time datetime="2026-09-30T00:00:00+00:00"></time></a>
              <div class="tgme_widget_message_text js-message_text">첫줄<br>둘째줄</div>
              %s
            </div>
            """.formatted(id, views.isEmpty() ? "" : "<span class=\"tgme_widget_message_views\">" + views + "</span>");
    }

    private static String page(String... messages) {
        return "<html><head><meta property=\"og:title\" content=\"채널\"/></head><body>"
            + "<div class=\"tgme_channel_history\">" + String.join("", messages) + "</div></body></html>";
    }

    @Test
    @DisplayName("[after 커서는 쿼리에 싣고, 새 글이 없어도 정상으로 빈 목록을 돌려준다]")
    void fetchPage_afterCursor_allowsEmpty() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/s/chan")))
            .andExpect(queryParam("after", "10"))
            .andRespond(withSuccess(page(), HTML_UTF8));

        TelegramPreviewPage result = client.fetchPage("chan", 10L, null);

        assertThat(result.messages()).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("[before 커서는 쿼리에 싣고 메시지를 파싱한다]")
    void fetchPage_beforeCursor_parsesMessages() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/s/chan")))
            .andExpect(queryParam("before", "20"))
            .andRespond(withSuccess(page(message(19, "820")), HTML_UTF8));

        TelegramPreviewPage result = client.fetchPage("chan", null, 20L);

        assertThat(result.channelTitle()).isEqualTo("채널");
        assertThat(result.messages()).hasSize(1);
        assertThat(result.messages().get(0).viewCount()).isEqualTo(820L);
        server.verify();
    }

    @Test
    @DisplayName("[after와 before를 동시에 지정하면 호출 전에 거부한다]")
    void fetchPage_bothCursors_rejected() {
        assertThatThrownBy(() -> client.fetchPage("chan", 1L, 2L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[조회수 K/M 표기는 정수로 환산하고, 예상 밖 표기는 null로 둔다]")
    void viewCount_suffixesAreConverted() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/s/chan")))
            .andRespond(withSuccess(page(message(1, "3.93K"), message(2, "1.1M"), message(3, "많음"), message(4, "")),
                HTML_UTF8));

        TelegramPreviewPage result = client.fetchPage("chan", null, null);

        assertThat(result.messages()).extracting(m -> m.viewCount())
            .containsExactly(3930L, 1_100_000L, null, null);
        assertThat(result.messages().get(0).content()).isEqualTo("첫줄\n둘째줄");
    }

    @Test
    @DisplayName("[최신 페이지 요청인데 메시지를 하나도 못 뽑으면 PREVIEW_PARSE_FAILED로 바꾼다]")
    void fetchPage_silentZero_mapsToParseFailed() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/s/chan")))
            .andRespond(withSuccess(page(), HTML_UTF8));

        assertThatThrownBy(() -> client.fetchPage("chan", null, null))
            .isInstanceOf(ExternalApiException.class)
            .satisfies(t -> assertThat(((ExternalApiException) t).getCode())
                .isEqualTo(TelegramApiErrorCode.PREVIEW_PARSE_FAILED.getCode()));
    }

    @Test
    @DisplayName("[HTTP 오류는 PREVIEW_FETCH_FAILED로 바꾼다]")
    void fetchPage_serverError_mapsToFetchFailed() {
        server.expect(requestTo(Matchers.startsWith(BASE_URL + "/s/chan"))).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchPage("chan", null, null))
            .isInstanceOf(ExternalApiException.class)
            .satisfies(t -> assertThat(((ExternalApiException) t).getCode())
                .isEqualTo(TelegramApiErrorCode.PREVIEW_FETCH_FAILED.getCode()));
    }
}
