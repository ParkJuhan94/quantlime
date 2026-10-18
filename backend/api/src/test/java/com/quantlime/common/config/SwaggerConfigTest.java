package com.quantlime.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlime.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SwaggerConfigTest extends ApiTestSupport {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode docs(String group) throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs/" + group))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    @DisplayName("관리자 그룹은 /api/admin 경로만 포함하고 401/403 에러 응답이 붙는다")
    void adminGroup() throws Exception {
        JsonNode paths = docs("2. 관리자").get("paths");

        paths.fieldNames().forEachRemaining(path -> assertThat(path).startsWith("/api/admin/"));
        JsonNode operation = paths.elements().next().elements().next();
        assertThat(operation.get("security").toString()).contains("bearerAuth");
        assertThat(operation.get("responses").has("401")).isTrue();
        assertThat(operation.get("responses").has("403")).isTrue();
    }

    @Test
    @DisplayName("사용자 그룹은 관리자/웹훅 경로를 제외하고, 로그인 파라미터를 요청 파라미터로 노출하지 않는다")
    void userGroup() throws Exception {
        JsonNode doc = docs("1. 사용자");
        JsonNode paths = doc.get("paths");

        paths.fieldNames().forEachRemaining(path ->
            assertThat(path).doesNotStartWith("/api/admin/").doesNotStartWith("/api/webhooks/"));
        assertThat(doc.at("/components/schemas/ErrorResponseTemplate").isMissingNode()).isFalse();

        JsonNode getWatchlist = paths.get("/api/watchlist").get("get");
        assertThat(getWatchlist.get("security").toString()).contains("bearerAuth");
        assertThat(getWatchlist.toString()).doesNotContain("\"name\":\"userId\"");
    }

    @Test
    @DisplayName("웹훅 그룹은 인증 요구가 붙지 않는다")
    void webhookGroup() throws Exception {
        JsonNode paths = docs("3. 웹훅").get("paths");

        paths.fieldNames().forEachRemaining(path -> assertThat(path).startsWith("/api/webhooks/"));
        assertThat(paths.toString()).doesNotContain("bearerAuth");
    }
}
