package com.quantlime.market.architecture;

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
 * 상위 레이어를 모름)을 {@code com.quantlime.market} 패키지에 한해 고정한다
 * ({@code PaymentLayerArchitectureTest}와 같은 패턴, 구현 레이어 분리 직후).
 *
 * <p>{@code cache}/{@code scheduler}/{@code config}도 Repository 직접 참조를 막는다
 * (MarketIndexCache·DomesticListedStockCache·TossMarketRankingCache를 Reader로 정리한 뒤).
 *
 * <p>외부 연동 클라이언트(Naver/Toss) 참조도 막는다 - 패턴 {@code com.quantlime.infra.*}는 한
 * 단계 하위 패키지에 직접 속한 클래스(클라이언트·Config·Properties)만 매칭하고 응답
 * DTO({@code ..dto..})·예외({@code ..exception..})는 데이터일 뿐이라 허용한다. 수집 흐름은
 * {@code implement}의 {@code BenchmarkIndexCollector}/{@code InvestorTradingCollector}가 맡는다.
 *
 * <p>다른 도메인의 Repository도 {@code ..repository..}로 함께 막는다 - market 서비스가
 * price/score 데이터를 읽을 땐 각 도메인의 {@code implement}(Reader)를 거친다.
 */
@Tag("unit")
class MarketLayerArchitectureTest {

    private static JavaClasses marketClasses;

    @BeforeAll
    static void importClasses() {
        marketClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.quantlime.market");
    }

    @Test
    @DisplayName("[Business(service)는 어느 도메인의 Data Access(repository)도 건너뛰어 직접 참조하지 않는다]")
    void serviceMustNotAccessRepositoryDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.market.service..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

        rule.check(marketClasses);
    }

    @Test
    @DisplayName("[Implementation(implement)은 Business(service)를 참조하지 않는다 - 참조 방향은 "
        + "항상 위(Business)에서 아래(Implementation)로만 흐른다]")
    void implementMustNotAccessServiceBack() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.market.implement..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.market.service..");

        rule.check(marketClasses);
    }

    @Test
    @DisplayName("[Data Access(repository)는 상위 레이어(service/implement)를 참조하지 않는다]")
    void repositoryMustNotAccessUpperLayers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.market.repository..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.quantlime.market.service..", "com.quantlime.market.implement..");

        rule.check(marketClasses);
    }

    @Test
    @DisplayName("[캐시·스케줄러·설정(cache/scheduler/config)도 Data Access(repository)를 직접 참조하지 않는다 - "
        + "구현 레이어(implement)의 Reader/Appender를 거친다]")
    void cacheSchedulerConfigMustNotAccessRepositoryDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                "com.quantlime.market.cache..", "com.quantlime.market.scheduler..", "com.quantlime.market.config..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

        rule.check(marketClasses);
    }

    @Test
    @DisplayName("[Business(service)는 외부 연동 클라이언트(infra)를 직접 참조하지 않는다 - 구현 상세는 "
        + "Implementation(implement)의 Collector로 감춘다. 응답 DTO/예외는 데이터라 예외로 둔다]")
    void serviceMustNotAccessInfraClientDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.market.service..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.infra.*");

        rule.check(marketClasses);
    }
}
