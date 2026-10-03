package com.quantlime.videofeed.architecture;

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
 * 레이어 참조 방향 규칙(순방향으로만 참조, 하위 레이어를 건너뛰지 않음, 하위 레이어는
 * 상위 레이어를 모름)을 {@code com.quantlime.videofeed} 패키지에 한해 고정한다
 * ({@code BacktestLayerArchitectureTest}와 같은 패턴, 구현 레이어 분리 직후).
 *
 * <p>Repository만 다루던 {@code *PersistService}/{@code VideoRetentionDeleteService}는
 * 저장·삭제 전용이라 {@code implement}의 {@code *Appender}/{@code VideoRemover}로
 * 옮겼다(트랜잭션 경계를 위해 별도 빈으로 둔 이유는 그대로 - 원래 클래스 주석 참고).
 *
 * <p>외부 연동 클라이언트(YouTube/Gemini 등) 참조 금지 규칙은 넣지 않았다 -
 * 수집·요약 서비스가 외부 호출 흐름 자체를 조립하는 성격이라 별도 설계가 필요하다.
 */
@Tag("unit")
class VideoFeedLayerArchitectureTest {

    private static JavaClasses videofeedClasses;

    @BeforeAll
    static void importClasses() {
        videofeedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.quantlime.videofeed");
    }

    @Test
    @DisplayName("[Business(service)는 어느 도메인의 Data Access(repository)도 건너뛰어 직접 참조하지 않는다]")
    void serviceMustNotAccessRepositoryDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.videofeed.service..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

        rule.check(videofeedClasses);
    }

    @Test
    @DisplayName("[Implementation(implement)은 Business(service)를 참조하지 않는다 - 참조 방향은 "
        + "항상 위(Business)에서 아래(Implementation)로만 흐른다]")
    void implementMustNotAccessServiceBack() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.videofeed.implement..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.videofeed.service..");

        rule.check(videofeedClasses);
    }

    @Test
    @DisplayName("[Data Access(repository)는 상위 레이어(service/implement)를 참조하지 않는다]")
    void repositoryMustNotAccessUpperLayers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.videofeed.repository..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.quantlime.videofeed.service..", "com.quantlime.videofeed.implement..");

        rule.check(videofeedClasses);
    }
}
