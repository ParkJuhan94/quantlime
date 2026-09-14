package com.quantlime.infra.sync;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로컬↔운영 간 자막 동기화(2026-09-15)에 쓰는 설정. {@code apiKey}는 양쪽이
 * 같은 값을 공유하는 대칭키다 - 운영은 이 값으로 들어오는 요청을 검증만
 * 하고(SyncApiKeyAuthenticationFilter), 로컬은 이 값을 실어 보내기만 한다
 * (SyncApiClient). {@code prodApiBase}는 로컬 쪽에서만 의미가 있다(운영이
 * 자기 자신을 호출할 일은 없음) - 미설정 시 로컬 동기화 기능 자체가
 * 비활성화된다(LocalTranscriptSyncService 참고).
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "sync")
public class SyncProperties {

    private final String apiKey;
    private final String prodApiBase;
}
