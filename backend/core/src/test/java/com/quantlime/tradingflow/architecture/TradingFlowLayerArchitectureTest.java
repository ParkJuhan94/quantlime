package com.quantlime.tradingflow.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 레이어 참조 방향 규칙을 {@code com.quantlime.tradingflow} 패키지에 고정한다({@code
 * MarketLayerArchitectureTest}와 같은 규칙) - 서비스는 Repository·외부 클라이언트를 직접 참조하지 않고
 * {@code implement}(Reader/Collector)를 거친다.
 */
@Tag("unit")
class TradingFlowLayerArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.quantlime.tradingflow");
    }

    @Test
    @DisplayName("[service는 어느 도메인의 repository도 직접 참조하지 않는다]")
    void serviceMustNotAccessRepositoryDirectly() {
        noClasses()
            .that().resideInAPackage("com.quantlime.tradingflow.service..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .check(classes);
    }

    @Test
    @DisplayName("[implement는 service를 참조하지 않는다]")
    void implementMustNotAccessServiceBack() {
        noClasses()
            .that().resideInAPackage("com.quantlime.tradingflow.implement..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.tradingflow.service..")
            .check(classes);
    }

    @Test
    @DisplayName("[repository는 상위 레이어(service/implement)를 참조하지 않는다]")
    void repositoryMustNotAccessUpperLayers() {
        noClasses()
            .that().resideInAPackage("com.quantlime.tradingflow.repository..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.quantlime.tradingflow.service..", "com.quantlime.tradingflow.implement..")
            .check(classes);
    }

    @Test
    @DisplayName("[scheduler/config는 repository를 직접 참조하지 않는다]")
    void schedulerConfigMustNotAccessRepositoryDirectly() {
        noClasses()
            .that().resideInAnyPackage("com.quantlime.tradingflow.scheduler..", "com.quantlime.tradingflow.config..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .check(classes);
    }

    @Test
    @DisplayName("[service는 외부 연동 클라이언트(infra)를 직접 참조하지 않는다 - Toss 호출은 Collector가 맡는다]")
    void serviceMustNotAccessInfraClientDirectly() {
        noClasses()
            .that().resideInAPackage("com.quantlime.tradingflow.service..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.infra.*")
            .check(classes);
    }
}
