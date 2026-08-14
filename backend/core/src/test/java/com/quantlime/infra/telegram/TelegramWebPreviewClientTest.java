package com.quantlime.infra.telegram;

import com.quantlime.infra.telegram.dto.TelegramPreviewMessage;
import com.quantlime.infra.telegram.dto.TelegramPreviewPage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TelegramWebPreviewClient.parsePage()를 실제로 캡처한 HTML 픽스처
 * (2026-08-13, t.me/s/insidertracking)로 검증한다 - Bot API 없이 HTML
 * 마크업에 의존하는 스크래핑이라, 텔레그램이 마크업을 바꾸면 이 테스트가
 * 조용히 깨지는 대신 실패로 드러나야 한다.
 */
@Tag("unit")
class TelegramWebPreviewClientTest {

    private final TelegramWebPreviewClient client = new TelegramWebPreviewClient(null);

    private String readFixture() {
        try (InputStream in = getClass().getResourceAsStream("/telegram/insidertracking-sample.html")) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("픽스처 로딩 실패", e);
        }
    }

    @Test
    @DisplayName("[실제 캡처한 HTML에서 텍스트 없는 미디어 전용 글(2건)을 제외한 나머지 메시지를 전부 파싱한다]")
    void parsePage_realFixture_parsesAllMessagesExceptTextless() {
        // given
        String html = readFixture();

        // when
        TelegramPreviewPage page = client.parsePage(html, true);

        // then
        assertThat(page.messages()).hasSize(17);
        assertThat(page.channelTitle()).isEqualTo("미국 주식 인사이더 🇺🇸 (US Stocks Insider)");
        assertThat(page.channelPhotoUrl()).startsWith("https://cdn5.telesco.pe/");
    }

    @Test
    @DisplayName("[<br>로 구분된 여러 문단이 개행으로 보존되고, 조회수/게시시각(KST 변환)/미디어 유무를 정확히 파싱한다]")
    void parsePage_messageWithLineBreaksAndBlockquote_preservesNewlinesAndParsesFields() {
        // given
        String html = readFixture();

        // when
        TelegramPreviewPage page = client.parsePage(html, true);
        TelegramPreviewMessage first = page.messages().stream()
            .filter(message -> message.externalPostId().equals("insidertracking/61109"))
            .findFirst()
            .orElseThrow();

        // then
        assertThat(first.messageId()).isEqualTo(61109L);
        assertThat(first.content()).isEqualTo(
            "백악관:\n\n미국의 해양 강국 재건.\n\n오늘, 트럼프 대통령은 미국의 해양 부흥 시대를 열고, "
                + "상업용 선박 건조 능력을 확대하며, 탄력적인 인력을 양성하고, 중요한 산업을 활성화하기 위한 "
                + "국가 안보 대통령 지령을 서명했습니다.");
        // 원본 datetime="2026-08-13T23:53:57+00:00"(UTC) -> KST(+9h)
        assertThat(first.publishedAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 8, 53, 57));
        assertThat(first.viewCount()).isEqualTo(6670L);
        assertThat(first.hasMedia()).isTrue();
    }

    @Test
    @DisplayName("[?after= 증분 요청은 빈 결과가 나와도 마크업 이상으로 취급하지 않는다(새 글 없음이 정상 케이스)]")
    void parsePage_emptyResultForAfterCursor_doesNotThrow() {
        // given
        String html = "<html><body><div class=\"tgme_channel_history\"></div></body></html>";

        // when
        TelegramPreviewPage page = client.parsePage(html, false);

        // then
        assertThat(page.messages()).isEmpty();
    }

    @Test
    @DisplayName("[최신/과거 페이지 요청인데 채널 히스토리 컨테이너는 있고 메시지를 하나도 못 뽑으면 마크업 변경으로 간주해 예외를 던진다]")
    void parsePage_emptyResultWithHistoryContainerPresent_throwsForNonAfterRequest() {
        // given
        String html = "<html><body><div class=\"tgme_channel_history\"></div></body></html>";

        // when & then
        assertThatThrownBy(() -> client.parsePage(html, true))
            .isInstanceOf(IllegalStateException.class);
    }
}
