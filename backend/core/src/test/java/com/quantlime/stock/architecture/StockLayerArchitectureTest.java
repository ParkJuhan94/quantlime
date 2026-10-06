package com.quantlime.stock.architecture;

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
 * 상위 레이어를 모름)을 {@code com.quantlime.stock} 패키지에 한해 고정한다
 * ({@code PriceLayerArchitectureTest}와 같은 패턴).
 *
 * <p>외부 연동 클라이언트 참조(`com.quantlime.infra.*`, DTO/예외 제외)도 막는다 - 수집·호출 흐름은 implement의
 * Collector/Processor가 맡는다.
 */
@Tag("unit")
class StockLayerArchitectureTest {

    private static JavaClasses stockClasses;

    @BeforeAll
    static void importClasses() {
        stockClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.quantlime.stock");
    }

    @Test
    @DisplayName("[Business(service)는 어느 도메인의 Data Access(repository)도 건너뛰어 직접 참조하지 않는다]")
    void serviceMustNotAccessRepositoryDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.stock.service..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

        rule.check(stockClasses);
    }

    @Test
    @DisplayName("[Implementation(implement)은 Business(service)를 참조하지 않는다 - 참조 방향은 "
        + "항상 위(Business)에서 아래(Implementation)로만 흐른다]")
    void implementMustNotAccessServiceBack() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.stock.implement..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.stock.service..");

        rule.check(stockClasses);
    }

    @Test
    @DisplayName("[Data Access(repository)는 상위 레이어(service/implement)를 참조하지 않는다]")
    void repositoryMustNotAccessUpperLayers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.stock.repository..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.quantlime.stock.service..", "com.quantlime.stock.implement..");

        rule.check(stockClasses);
    }

    @Test
    @DisplayName("[캐시(cache)도 Data Access(repository)를 직접 참조하지 않는다 - "
        + "구현 레이어(implement)의 Reader/Appender를 거친다]")
    void cacheSchedulerConfigMustNotAccessRepositoryDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                "com.quantlime.stock.cache..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

        rule.check(stockClasses);
    }

    @Test
    @DisplayName("[Business(service)는 외부 연동 클라이언트(infra)를 직접 참조하지 않는다 - 구현 상세는 "
        + "Implementation(implement)의 Collector로 감춘다. 응답 DTO/예외는 데이터라 예외로 둔다]")
    void serviceMustNotAccessInfraClientDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.stock.service..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.infra.*");

        rule.check(stockClasses);
    }
}
