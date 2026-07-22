package com.quantlime.common.upload;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "upload")
public class FileStorageProperties {

    private final String dir;
    private final long maxSizeBytes;
    // 업로드된 파일이 실제로 열람되는 절대 URL의 origin(스킴+호스트+포트).
    // /uploads/{파일명}은 상대 경로라 프론트 화면(같은 origin)에선 그대로
    // 써도 되지만, Slack처럼 서버 쪽에서 절대 URL이 필요한 소비자를 위해
    // 별도로 둔다(FeedbackService 참고).
    private final String publicBaseUrl;
}
