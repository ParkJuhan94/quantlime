package com.quantlime.notice.dto.mapper;

import com.quantlime.notice.domain.Notice;
import com.quantlime.notice.dto.response.NoticeResponse;
import com.quantlime.notice.dto.response.NoticeSummaryResponse;

public final class NoticeMapper {

    private static final int PREVIEW_LENGTH = 80;

    private NoticeMapper() {
    }

    public static NoticeResponse toNoticeResponse(Notice notice) {
        return new NoticeResponse(
            notice.getId(), notice.getTitle(), notice.getContent(), notice.getCreatedAt(), notice.getUpdatedAt());
    }

    public static NoticeSummaryResponse toNoticeSummaryResponse(Notice notice) {
        return new NoticeSummaryResponse(
            notice.getId(), notice.getTitle(), summarize(notice.getContent(), PREVIEW_LENGTH), notice.getCreatedAt());
    }

    /** 줄바꿈·연속 공백을 한 칸으로 합치고 maxLength자를 넘으면 말줄임표로 자른다(목록 미리보기·알림 본문용). */
    public static String summarize(String text, int maxLength) {
        String collapsed = text.trim().replaceAll("\\s+", " ");
        if (collapsed.length() <= maxLength) {
            return collapsed;
        }
        return collapsed.substring(0, maxLength).trim() + "…";
    }
}
