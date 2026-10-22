package com.quantlime.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

// CSS는 springdoc의 /swagger-ui/** 리소스 핸들러(웹자르 전용)에 가려지지 않도록 /swagger-dark.css에 둔다.
// springdoc 2.6.0에는 테마 프로퍼티가 없어, Swagger UI 진입 페이지(index.html)의 </head> 앞에 다크 CSS 링크를 끼워 넣는다.
// 문서 자체가 꺼지는 운영(SPRINGDOC_ENABLED=false)에서는 해당 경로가 404라 아무 일도 하지 않는다.
@Component
public class SwaggerDarkModeFilter extends OncePerRequestFilter {

    private static final String INDEX_PATH = "/swagger-ui/index.html";
    private static final String HEAD_END = "</head>";
    private static final String CSS_LINK = "<link rel=\"stylesheet\" href=\"/swagger-dark.css\">";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !INDEX_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain
    ) throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrapper);

        String html = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
        if (!html.contains(HEAD_END)) {
            wrapper.copyBodyToResponse();
            return;
        }
        byte[] body = html.replace(HEAD_END, CSS_LINK + HEAD_END).getBytes(StandardCharsets.UTF_8);
        wrapper.resetBuffer();
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}
