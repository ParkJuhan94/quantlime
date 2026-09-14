package com.quantlime.infra.python;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// @DefaultValue는 생성자 파라미터에 붙어야 바인딩된다(필드에 붙이면 컴파일
// 에러) - Lombok @RequiredArgsConstructor로는 파라미터 단위로 선택 적용할
// 수 없어 이 클래스만 생성자를 직접 쓴다.
@Getter
@ConfigurationProperties(prefix = "python-engine")
public class PythonEngineProperties {

    private final String baseUrl;
    // GeminiDailyQuotaGate가 참조한다 - 값의 근거는 application.yml의
    // summarize-daily-budget 주석 참고.
    private final int summarizeDailyBudget;

    public PythonEngineProperties(String baseUrl, @DefaultValue("18") int summarizeDailyBudget) {
        this.baseUrl = baseUrl;
        this.summarizeDailyBudget = summarizeDailyBudget;
    }
}
