package com.quantlime.telegramfeed.architecture;

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
 * 상위 레이어를 모름)을 {@code com.quantlime.telegramfeed} 패키지에 한해 고정한다
 * ({@code VideoFeedLayerArchitectureTest}와 같은 패턴, 구현 레이어 분리 직후).
 *
 * <p>Repository만 다루던 {@code *PersistService}/{@code TelegramPostRetentionDeleteService}는
 * 저장·삭제 전용이라 {@code implement}의 {@code *Appender}/{@code TelegramPostRemover}로
 * 옮겼다. 채널은 videofeed와 공유하는 엔티티라 videofeed의 {@code ChannelReader}/
 * {@code ChannelAppender}를 쓴다.
 *
 * <p>외부 연동 클라이언트(텔레그램 웹 프리뷰/Python 엔진)·그 Properties 참조도 막는다 - 패턴
 * {@code com.quantlime.infra.*}는 한 단계 하위 패키지에 직접 속한 클래스만 매칭하고 응답
 * DTO({@code ..dto..})는 데이터일 뿐이라 허용한다. 외부 호출은 {@code implement}의
 * {@code TelegramPostCollector}/{@code TelegramDigestGenerator}가 맡는다.
 */
@Tag("unit")
class TelegramFeedLayerArchitectureTest {

    private static JavaClasses telegramfeedClasses;

    @BeforeAll
    static void importClasses() {
        telegramfeedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.quantlime.telegramfeed");
    }

    @Test
    @DisplayName("[Business(service)는 어느 도메인의 Data Access(repository)도 건너뛰어 직접 참조하지 않는다]")
    void serviceMustNotAccessRepositoryDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.telegramfeed.service..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

        rule.check(telegramfeedClasses);
    }

    @Test
    @DisplayName("[Implementation(implement)은 Business(service)를 참조하지 않는다 - 참조 방향은 "
        + "항상 위(Business)에서 아래(Implementation)로만 흐른다]")
    void implementMustNotAccessServiceBack() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.telegramfeed.implement..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.telegramfeed.service..");

        rule.check(telegramfeedClasses);
    }

    @Test
    @DisplayName("[Data Access(repository)는 상위 레이어(service/implement)를 참조하지 않는다]")
    void repositoryMustNotAccessUpperLayers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.telegramfeed.repository..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.quantlime.telegramfeed.service..", "com.quantlime.telegramfeed.implement..");

        rule.check(telegramfeedClasses);
    }

    @Test
    @DisplayName("[Business(service)는 외부 연동 클라이언트(infra)를 직접 참조하지 않는다 - 구현 상세는 "
        + "Implementation(implement)의 Collector/Generator로 감춘다. 응답 DTO/예외는 데이터라 예외로 둔다]")
    void serviceMustNotAccessInfraClientDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.telegramfeed.service..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.infra.*");

        rule.check(telegramfeedClasses);
    }
}
