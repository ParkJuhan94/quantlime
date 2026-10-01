package com.quantlime.payment.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 강의(「지속 성장 가능한 소프트웨어를 만들어가는 방법」)의 레이어 참조 방향
 * 규칙 - 순방향으로만 참조하고, 하위 레이어를 건너뛰지 않으며, 하위
 * 레이어는 상위 레이어를 모른다 - 을 {@code com.quantlime.payment} 패키지에
 * 한해 테스트로 고정한다(2026-09-28, 구현 레이어 분리 직후).
 *
 * <p>프로젝트 전체에는 아직 적용하지 않는다 - 다른 도메인엔 서비스 간
 * 상호 참조(26곳), 다른 도메인 리포지토리 직접 참조(23곳) 같은 기존 위반이
 * 많아 그대로 걸면 대거 실패한다. 이번에 실제로 레이어를 정리한
 * payment 패키지부터 우선 고정해두고, 다른 도메인은 각자 정리될 때
 * 같은 패턴으로 규칙을 넓혀갈 것.
 *
 * <p>Presentation(Controller)은 이 검사에서 빠진다 - {@code PaymentWebhookController}는
 * api 모듈에 있어 core 모듈의 이 테스트 클래스패스에서 보이지 않는다.
 */
@Tag("unit")
class PaymentLayerArchitectureTest {

    private static JavaClasses paymentClasses;

    @BeforeAll
    static void importClasses() {
        paymentClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.quantlime.payment");
    }

    @Test
    @DisplayName("[Business(service)는 Data Access(repository)를 건너뛰어 직접 참조하지 않는다]")
    void serviceMustNotAccessRepositoryDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.payment.service..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

        rule.check(paymentClasses);
    }

    @Test
    @DisplayName("[Business(service)는 외부 연동 클라이언트(토스페이먼츠 등)를 직접 참조하지 않는다 - "
        + "구현 상세는 Implementation(implement) 레이어로 감춘다. 응답 DTO(..dto..)는 데이터일 "
        + "뿐이라 예외로 둔다]")
    void serviceMustNotAccessInfraClientDirectly() {
        // 패키지 패턴에 ..을 안 붙이면 그 패키지에 "직접" 속한 클래스만 매칭된다
        // (하위 패키지인 com.quantlime.infra.tosspayments.dto는 매칭되지 않음) -
        // TossPaymentsApiClient/TossPaymentsConfig/TossPaymentsProperties/
        // TossWebhookVerifier(구현 상세) vs TossBillingKeyResponse/
        // TossPaymentApprovalResponse(응답 데이터)를 정확히 가른다.
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.payment.service..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.infra.tosspayments");

        rule.check(paymentClasses);
    }

    @Test
    @DisplayName("[Implementation(implement)은 Business(service)를 참조하지 않는다 - 참조 방향은 "
        + "항상 위(Business)에서 아래(Implementation)로만 흐른다]")
    void implementMustNotAccessServiceBack() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.payment.implement..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.payment.service..");

        rule.check(paymentClasses);
    }

    @Test
    @DisplayName("[Data Access(repository)는 상위 레이어(service/implement)를 참조하지 않는다]")
    void repositoryMustNotAccessUpperLayers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.payment.repository..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.quantlime.payment.service..", "com.quantlime.payment.implement..");

        rule.check(paymentClasses);
    }
}
