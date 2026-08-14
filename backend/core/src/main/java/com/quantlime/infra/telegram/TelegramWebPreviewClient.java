package com.quantlime.infra.telegram;

import com.quantlime.common.util.ExternalApiInvoker;
import com.quantlime.infra.telegram.dto.TelegramPreviewMessage;
import com.quantlime.infra.telegram.dto.TelegramPreviewPage;
import com.quantlime.infra.telegram.exception.TelegramApiErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 텔레그램 채널의 공개 웹 미리보기 페이지(t.me/s/&lt;핸들&gt;)를 스크래핑한다.
 * Bot API는 사용자가 관리자가 아닌 공개 채널을 읽을 수 없어(getUpdates는
 * 봇이 admin으로 추가된 채널만 대상) 인증이 필요 없는 이 경로를 택했다
 * (docs/ROADMAP.md "Phase 8 P7" 참고). KindApiClient와 동일한 구조
 * (RestClient + Jsoup + ExternalApiInvoker)를 따른다. 실측(2026-08-13)으로
 * 확정한 마크업:
 * - 메시지 루트: div.tgme_widget_message[data-post], data-post는
 *   "&lt;핸들&gt;/&lt;메시지ID&gt;" 형태
 * - 게시 시각: a.tgme_widget_message_date time[datetime]에 ISO-8601 UTC
 * - 본문: div.tgme_widget_message_text.js-message_text
 * - 조회수: span.tgme_widget_message_views ("3.93K" 형태)
 * - 페이지네이션: ?after=&lt;id&gt;(최신 방향)/?before=&lt;id&gt;(과거 방향) 둘 다 지원
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramWebPreviewClient {

    private static final Pattern VIEW_COUNT_PATTERN = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)([KM]?)$");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RestClient telegramRestClient;

    public TelegramPreviewPage fetchPage(String channelHandle, Long afterMessageId, Long beforeMessageId) {
        if (afterMessageId != null && beforeMessageId != null) {
            throw new IllegalArgumentException("afterMessageId와 beforeMessageId는 동시에 지정할 수 없습니다.");
        }
        return ExternalApiInvoker.call(
            TelegramApiErrorCode.PREVIEW_FETCH_FAILED,
            () -> {
                String html = telegramRestClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/s/{handle}");
                        if (afterMessageId != null) {
                            uriBuilder.queryParam("after", afterMessageId);
                        }
                        if (beforeMessageId != null) {
                            uriBuilder.queryParam("before", beforeMessageId);
                        }
                        return uriBuilder.build(channelHandle);
                    })
                    .retrieve()
                    .body(String.class);
                // ?after= 커서 요청은 "새 글 없음"이 정상 결과라 빈 목록이어도
                // 구조 이상으로 취급하지 않는다 - 그 외(최신/과거 페이지 요청)는
                // 항상 내용이 있어야 정상이라 방어적으로 검증한다.
                return parsePage(html, afterMessageId == null);
            },
            IllegalStateException.class,
            TelegramApiErrorCode.PREVIEW_PARSE_FAILED);
    }

    // package-private으로 노출해 HTTP 없이 파서만 단위 테스트한다
    // (TelegramWebPreviewClientTest, 실제 캡처한 HTML 픽스처 기반 - 마크업
    // 드리프트를 CI에서 잡기 위함).
    TelegramPreviewPage parsePage(String html, boolean expectNonEmpty) {
        Document doc = Jsoup.parse(html);
        List<TelegramPreviewMessage> messages = parseMessages(doc);
        // 채널 히스토리 컨테이너는 있는데 메시지를 하나도 못 뽑았다면 이 실패
        // 모드에서 가장 위험한 "조용한 0건"(마크업 변경으로 셀렉터가 아무것도
        // 못 잡았는데 예외 없이 정상 종료되는 것)일 가능성이 크므로 예외로
        // 올려 장애를 드러낸다.
        if (expectNonEmpty && messages.isEmpty() && html.contains("tgme_channel_history")) {
            throw new IllegalStateException(
                "텔레그램 미리보기 페이지에서 메시지를 하나도 파싱하지 못했습니다 - 마크업 변경 가능성");
        }
        String channelTitle = doc.select("meta[property=og:title]").attr("content");
        String channelPhotoUrl = doc.select("meta[property=og:image]").attr("content");
        return new TelegramPreviewPage(
            channelTitle.isBlank() ? null : channelTitle,
            channelPhotoUrl.isBlank() ? null : channelPhotoUrl,
            messages);
    }

    private List<TelegramPreviewMessage> parseMessages(Document doc) {
        List<TelegramPreviewMessage> messages = new ArrayList<>();
        Elements messageElements = doc.select("div.tgme_widget_message[data-post]");
        for (Element element : messageElements) {
            try {
                TelegramPreviewMessage message = parseMessage(element);
                if (message != null) {
                    messages.add(message);
                }
            } catch (Exception e) {
                log.warn("텔레그램 메시지 파싱 실패(해당 메시지만 skip): reason={}", e.getMessage());
            }
        }
        return messages;
    }

    private TelegramPreviewMessage parseMessage(Element element) {
        String externalPostId = element.attr("data-post");
        Long messageId = parseMessageId(externalPostId);
        if (messageId == null) {
            // 앨범(grouped media) 등 숫자가 아닌 접미사가 붙는 특수 형식으로
            // 추정 - 방어적으로 skip(전체 페이지 파싱은 계속 진행).
            log.debug("data-post에서 메시지ID 파싱 실패 - skip: data-post={}", externalPostId);
            return null;
        }

        Element dateEl = element.selectFirst("a.tgme_widget_message_date time[datetime]");
        if (dateEl == null) {
            log.warn("게시 시각 파싱 실패 - skip: externalPostId={}", externalPostId);
            return null;
        }
        LocalDateTime publishedAt = OffsetDateTime.parse(dateEl.attr("datetime"))
            .atZoneSameInstant(SEOUL)
            .toLocalDateTime();

        Element textEl = element.selectFirst("div.tgme_widget_message_text.js-message_text");
        String content = textEl != null ? extractContent(textEl) : "";
        if (content.isBlank()) {
            // 미디어 전용 글(텍스트 없음) - 요약할 본문이 없으므로 정상적으로 skip.
            log.debug("본문 없음(미디어 전용 글로 추정) - skip: externalPostId={}", externalPostId);
            return null;
        }

        Element viewsEl = element.selectFirst("span.tgme_widget_message_views");
        Long viewCount = viewsEl != null ? parseViewCount(viewsEl.text()) : null;

        boolean hasMedia = !element.select(
            ".tgme_widget_message_photo_wrap, .tgme_widget_message_video, .tgme_widget_message_document")
            .isEmpty();

        return new TelegramPreviewMessage(externalPostId, messageId, content, publishedAt, viewCount, hasMedia);
    }

    private Long parseMessageId(String externalPostId) {
        int slashIndex = externalPostId.indexOf('/');
        if (slashIndex < 0) {
            return null;
        }
        try {
            return Long.parseLong(externalPostId.substring(slashIndex + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // <br>이 포함된 본문을 Jsoup Element.text()로 그대로 뽑으면 개행 없이
    // 붙어버리는 함정이 있어(실측 확인) 자식 노드를 직접 순회한다 - <br>만
    // "\n"으로 치환하고, <b>/<a>/<blockquote> 등 나머지 인라인 태그는
    // 재귀적으로 내려가 텍스트만 이어붙인다.
    private String extractContent(Element textElement) {
        StringBuilder sb = new StringBuilder();
        appendText(textElement, sb);
        return sb.toString().trim();
    }

    private void appendText(Node node, StringBuilder sb) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode textNode) {
                sb.append(textNode.text());
            } else if (child instanceof Element element) {
                if ("br".equalsIgnoreCase(element.tagName())) {
                    sb.append("\n");
                } else {
                    appendText(element, sb);
                }
            }
        }
    }

    // "3.93K" -> 3930, "1.1M" -> 1100000, "820" -> 820. 예상 밖 형식은
    // null로 두고 계속 진행한다(조회수는 부가 정보라 전체 파싱을 막을
    // 이유가 없음).
    private Long parseViewCount(String text) {
        String trimmed = text.trim();
        Matcher matcher = VIEW_COUNT_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            log.debug("조회수 파싱 실패(예상 밖 형식) - null 처리: text={}", trimmed);
            return null;
        }
        double value = Double.parseDouble(matcher.group(1));
        double multiplier = switch (matcher.group(2)) {
            case "K" -> 1_000;
            case "M" -> 1_000_000;
            default -> 1;
        };
        return Math.round(value * multiplier);
    }
}
