package com.quantlime.feedback.architecture;

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
 * {@code com.quantlime.feedback}의 레이어 규칙. 외부 연동(Slack) 호출은 implement의
 * {@code FeedbackNotifier}가 맡고, service는 클라이언트를 직접 참조하지 않는다
 * ({@code StockLayerArchitectureTest}와 같은 패턴).
 */
@Tag("unit")
class FeedbackLayerArchitectureTest {

    private static JavaClasses feedbackClasses;

    @BeforeAll
    static void importClasses() {
        feedbackClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.quantlime.feedback");
    }

    @Test
    @DisplayName("[Business(service)는 외부 연동 클라이언트(infra)를 직접 참조하지 않는다]")
    void serviceMustNotAccessInfraClientDirectly() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.feedback.service..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.infra.*");

        rule.check(feedbackClasses);
    }

    @Test
    @DisplayName("[Implementation(implement)은 Business(service)를 참조하지 않는다]")
    void implementMustNotAccessServiceBack() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.quantlime.feedback.implement..")
            .should().dependOnClassesThat().resideInAPackage("com.quantlime.feedback.service..");

        rule.check(feedbackClasses);
    }
}
