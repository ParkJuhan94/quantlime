package com.quantlime.architecture;

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
 * Presentation(controller)이 Data Access(repository)를 건너뛰어 직접 참조하지 못하게 api 모듈 전체에
 * 고정한다 - AdminNotificationController가 UserRepository를 직접 잡던 것을 service 경유로 바꾼 뒤.
 * 도메인별 core ArchUnit 테스트는 core 모듈 클래스만 보므로 컨트롤러는 이 테스트가 맡는다.
 */
@Tag("unit")
class ApiLayerArchitectureTest {

    private static JavaClasses apiClasses;

    @BeforeAll
    static void importClasses() {
        apiClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.quantlime");
    }

    @Test
    @DisplayName("[Presentation(controller)은 어느 도메인의 Data Access(repository)도 직접 참조하지 않는다]")
    void controllerMustNotAccessRepositoryDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

        rule.check(apiClasses);
    }
}
