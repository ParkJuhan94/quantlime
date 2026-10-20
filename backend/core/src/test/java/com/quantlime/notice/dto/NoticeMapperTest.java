package com.quantlime.notice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.quantlime.notice.dto.mapper.NoticeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class NoticeMapperTest {

    @Test
    @DisplayName("[줄바꿈·연속 공백은 한 칸으로 합친다]")
    void summarize_collapsesWhitespace() {
        assertThat(NoticeMapper.summarize("  첫 줄\n\n  둘째   줄\t셋째 ", 80)).isEqualTo("첫 줄 둘째 줄 셋째");
    }

    @Test
    @DisplayName("[한도 이하면 그대로, 넘으면 한도까지 자르고 말줄임표를 붙인다]")
    void summarize_truncates() {
        assertThat(NoticeMapper.summarize("가".repeat(10), 10)).isEqualTo("가".repeat(10));
        assertThat(NoticeMapper.summarize("가".repeat(11), 10)).isEqualTo("가".repeat(10) + "…");
    }
}
