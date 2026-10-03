package com.quantlime.watchlist.architecture;

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
 * 상위 레이어를 모름)을 {@code com.quantlime.watchlist} 패키지에 한해 고정한다
 * ({@code StockLayerArchitectureTest}와 같은 패턴).
 */
@Tag("unit")
class WatchlistLayerArchitectureTest {

    private static JavaClasses watchlistClasses;

    @BeforeAll
    static void importClasses() {
        watchlistClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.quantlime.watchlist");
    }

    @Test
    @DisplayName("[Business(service)는 어느 도메인의 Data Access(repository)도 건너뛰어 직접 참조하지 않는다]")
    void serviceMustNotAccessRepositoryDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.watchlist.service..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

        rule.check(watchlistClasses);
    }

    @Test
    @DisplayName("[Implementation(implement)은 Business(service)를 참조하지 않는다 - 참조 방향은 "
        + "항상 위(Business)에서 아래(Implementation)로만 흐른다]")
    void implementMustNotAccessServiceBack() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.watchlist.implement..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.watchlist.service..");

        rule.check(watchlistClasses);
    }

    @Test
    @DisplayName("[Data Access(repository)는 상위 레이어(service/implement)를 참조하지 않는다]")
    void repositoryMustNotAccessUpperLayers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.watchlist.repository..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.quantlime.watchlist.service..", "com.quantlime.watchlist.implement..");

        rule.check(watchlistClasses);
    }

    @Test
    @DisplayName("[설정(config)도 Data Access(repository)를 직접 참조하지 않는다 - "
        + "구현 레이어(implement)의 Reader/Appender를 거친다]")
    void configMustNotAccessRepositoryDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                "com.quantlime.watchlist.config..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

        rule.check(watchlistClasses);
    }
}
