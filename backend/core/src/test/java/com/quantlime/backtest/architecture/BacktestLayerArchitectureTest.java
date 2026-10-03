package com.quantlime.backtest.architecture;

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
 * 상위 레이어를 모름)을 {@code com.quantlime.backtest} 패키지에 한해 고정한다
 * ({@code PriceLayerArchitectureTest}와 같은 패턴, 구현 레이어 분리 직후).
 *
 * <p>{@code scheduler}도 Repository 직접 참조를 막는다 - {@code BacktestWeeklyScheduler}의
 * 최신 scoreVersion 조회를 {@code BacktestReader}로 옮긴 뒤.
 *
 * <p>외부 연동 클라이언트(Python 퀀트 엔진) 참조도 막는다 - 패턴 {@code com.quantlime.infra.*}는 한
 * 단계 하위 패키지에 직접 속한 클래스(클라이언트·Config·Properties)만 매칭하고 응답
 * DTO({@code ..dto..})는 데이터일 뿐이라 허용한다. 엔진 요청 변환·호출·응답 변환은
 * {@code implement}의 {@code BacktestEngineProcessor}가 맡는다.
 */
@Tag("unit")
class BacktestLayerArchitectureTest {

    private static JavaClasses backtestClasses;

    @BeforeAll
    static void importClasses() {
        backtestClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.quantlime.backtest");
    }

    @Test
    @DisplayName("[Business(service)는 어느 도메인의 Data Access(repository)도 건너뛰어 직접 참조하지 않는다]")
    void serviceMustNotAccessRepositoryDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.backtest.service..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

        rule.check(backtestClasses);
    }

    @Test
    @DisplayName("[Implementation(implement)은 Business(service)를 참조하지 않는다 - 참조 방향은 "
        + "항상 위(Business)에서 아래(Implementation)로만 흐른다]")
    void implementMustNotAccessServiceBack() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.backtest.implement..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.backtest.service..");

        rule.check(backtestClasses);
    }

    @Test
    @DisplayName("[Data Access(repository)는 상위 레이어(service/implement)를 참조하지 않는다]")
    void repositoryMustNotAccessUpperLayers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.backtest.repository..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.quantlime.backtest.service..", "com.quantlime.backtest.implement..");

        rule.check(backtestClasses);
    }

    @Test
    @DisplayName("[캐시·스케줄러·설정(cache/scheduler/config)도 Data Access(repository)를 직접 참조하지 않는다 - "
        + "구현 레이어(implement)의 Reader/Appender를 거친다]")
    void cacheSchedulerConfigMustNotAccessRepositoryDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                "com.quantlime.backtest.cache..", "com.quantlime.backtest.scheduler..", "com.quantlime.backtest.config..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

        rule.check(backtestClasses);
    }

    @Test
    @DisplayName("[Business(service)는 외부 연동 클라이언트(infra)를 직접 참조하지 않는다 - 구현 상세는 "
        + "Implementation(implement)의 Processor로 감춘다. 응답 DTO/예외는 데이터라 예외로 둔다]")
    void serviceMustNotAccessInfraClientDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.backtest.service..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.infra.*");

        rule.check(backtestClasses);
    }
}
