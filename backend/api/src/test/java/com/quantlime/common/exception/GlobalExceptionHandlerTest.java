package com.quantlime.common.exception;

import com.quantlime.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class GlobalExceptionHandlerTest extends ApiTestSupport {

    @Test
    @DisplayName("[매핑되지 않은 경로는 500이 아니라 404를 반환한다]")
    void unmappedPath_returns404NotInternalServerError() throws Exception {
        // given
        // "/dev/**"는 SecurityConfig의 PERMIT_ALL_PATTERNS라 인증 없이도
        // DispatcherServlet까지 도달한다(그래야 아래에서 검증하려는
        // NoResourceFoundException 경로를 401에 가로막히지 않고 재현할 수
        // 있음). DevController는 @Profile("dev")라 이 테스트의 "test"
        // 프로파일에서는 로드되지 않으므로, prod 배포에서 /dev/auth/token을
        // 호출했을 때와 정확히 같은 상황(매핑 자체가 없음)이 된다.

        // when & then
        // Spring 6의 정적 리소스 핸들러가 던지는 NoResourceFoundException을
        // 이전에는 catch-all(Exception.class)이 삼켜 500으로 응답했다
        // (배포 검증 중 발견: docs/DEPLOYMENT.md 작성 근거이기도 함).
        mockMvc.perform(get("/dev/no-such-endpoint"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
