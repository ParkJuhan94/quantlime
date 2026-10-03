package com.quantlime.price.architecture;

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
 * 상위 레이어를 모름)을 {@code com.quantlime.price} 패키지에 한해 고정한다
 * ({@code MarketLayerArchitectureTest}와 같은 패턴, 구현 레이어 분리 직후).
 *
 * <p>{@code cache}/{@code scheduler}/{@code config}도 Repository 직접 참조를 막는다
 * (PriceCacheConfig, 정규장 종가 캡처·해외 관심종목 스케줄러를 Reader/Appender로 정리한 뒤).
 * {@code realtime}은 Repository를 쓰지 않아 별도 규칙이 없다.
 *
 * <p>외부 연동 클라이언트(Toss) 참조 금지 규칙은 아직 넣지 않았다 - 일봉 백필
 * 서비스들이 외부 API 호출과 영속 저장을 한 흐름으로 묶는 수집기 성격이라, 그
 * 규칙까지 걸려면 수집 전용 구현 컴포넌트(Collector)를 따로 도입하는 별도
 * 리팩터링이 필요하다.
 */
@Tag("unit")
class PriceLayerArchitectureTest {

    private static JavaClasses priceClasses;

    @BeforeAll
    static void importClasses() {
        priceClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.quantlime.price");
    }

    @Test
    @DisplayName("[Business(service)는 어느 도메인의 Data Access(repository)도 건너뛰어 직접 참조하지 않는다]")
    void serviceMustNotAccessRepositoryDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.price.service..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

        rule.check(priceClasses);
    }

    @Test
    @DisplayName("[Implementation(implement)은 Business(service)를 참조하지 않는다 - 참조 방향은 "
        + "항상 위(Business)에서 아래(Implementation)로만 흐른다]")
    void implementMustNotAccessServiceBack() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.price.implement..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.price.service..");

        rule.check(priceClasses);
    }

    @Test
    @DisplayName("[Data Access(repository)는 상위 레이어(service/implement)를 참조하지 않는다]")
    void repositoryMustNotAccessUpperLayers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.price.repository..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.quantlime.price.service..", "com.quantlime.price.implement..");

        rule.check(priceClasses);
    }

    @Test
    @DisplayName("[캐시·스케줄러·설정(cache/scheduler/config)도 Data Access(repository)를 직접 참조하지 않는다 - "
        + "구현 레이어(implement)의 Reader/Appender를 거친다]")
    void cacheSchedulerConfigMustNotAccessRepositoryDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage(
                "com.quantlime.price.cache..", "com.quantlime.price.scheduler..", "com.quantlime.price.config..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

        rule.check(priceClasses);
    }
}
