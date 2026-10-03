package com.quantlime.notification.architecture;

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
 * 상위 레이어를 모름)을 {@code com.quantlime.notification} 패키지에 한해 고정한다
 * ({@code StockLayerArchitectureTest}와 같은 패턴).
 *
 * <p>scheduler도 Repository를 직접 참조하지 않는다(NotificationCleanupScheduler, ScoreRankingNotificationScheduler를
 * Appender/Reader로 정리한 뒤). 컨트롤러는 api 모듈이라 {@code ApiLayerArchitectureTest}가 맡는다.
 */
@Tag("unit")
class NotificationLayerArchitectureTest {

    private static JavaClasses notificationClasses;

    @BeforeAll
    static void importClasses() {
        notificationClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.quantlime.notification");
    }

    @Test
    @DisplayName("[Business(service)는 어느 도메인의 Data Access(repository)도 건너뛰어 직접 참조하지 않는다]")
    void serviceMustNotAccessRepositoryDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.notification.service..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

        rule.check(notificationClasses);
    }

    @Test
    @DisplayName("[Implementation(implement)은 Business(service)를 참조하지 않는다 - 참조 방향은 "
        + "항상 위(Business)에서 아래(Implementation)로만 흐른다]")
    void implementMustNotAccessServiceBack() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.notification.implement..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.notification.service..");

        rule.check(notificationClasses);
    }

    @Test
    @DisplayName("[Data Access(repository)는 상위 레이어(service/implement)를 참조하지 않는다]")
    void repositoryMustNotAccessUpperLayers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.notification.repository..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.quantlime.notification.service..", "com.quantlime.notification.implement..");

        rule.check(notificationClasses);
    }

    @Test
    @DisplayName("[스케줄러(scheduler)도 Data Access(repository)를 직접 참조하지 않는다 - "
        + "구현 레이어(implement)의 Reader/Appender를 거친다]")
    void schedulerMustNotAccessRepositoryDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                "com.quantlime.notification.scheduler..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

        rule.check(notificationClasses);
    }
}
