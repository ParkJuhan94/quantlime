package com.quantlime.infra.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * FCM 서비스 계정 키(FCM_SERVICE_ACCOUNT_PATH)가 아직 설정되지 않은
 * 로컬/초기 환경에서도 기동 자체는 막지 않는다(토스페이먼츠 시크릿키
 * 미설정 시 경고만 남기는 TossPaymentsConfig와 동일한 판단) - FirebaseApp을
 * 초기화하지 않고 firebaseMessaging() 빈도 null을 반환한다. FcmPushService는
 * 이 null을 받아 푸시 발송만 건너뛰고 인앱 알림 저장은 그대로 진행한다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(FirebaseProperties.class)
public class FirebaseConfig {

    private final FirebaseProperties properties;

    @PostConstruct
    public void initialize() throws IOException {
        if (!StringUtils.hasText(properties.getServiceAccountPath())) {
            log.warn("FCM 서비스 계정 키 미설정: FCM_SERVICE_ACCOUNT_PATH 확인 필요 "
                + "(설정 전까지 푸시 발송은 건너뛰고 인앱 알림만 저장됨)");
            return;
        }
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }
        try (InputStream credentials = new FileInputStream(properties.getServiceAccountPath())) {
            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(credentials))
                .build();
            FirebaseApp.initializeApp(options);
            log.info("FirebaseApp 초기화 완료");
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        if (FirebaseApp.getApps().isEmpty()) {
            return null;
        }
        return FirebaseMessaging.getInstance();
    }
}
