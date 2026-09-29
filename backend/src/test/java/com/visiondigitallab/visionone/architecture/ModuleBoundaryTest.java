package com.visiondigitallab.visionone.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The module rules, enforced by the build rather than by review.
 *
 * <p>These are what make Client #2 cheap. A boundary nobody checks is a boundary that is already
 * broken.
 */
class ModuleBoundaryTest {

    private static final String ROOT = "com.visiondigitallab.visionone";
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    @DisplayName("no cyclic dependencies between modules")
    void modulesAreAcyclic() {
        ArchRule rule = SlicesRuleDefinition.slices()
                .matching(ROOT + ".(*)..")
                .should()
                .beFreeOfCycles();
        rule.check(classes);
    }

    @Test
    @DisplayName("a module's internal package is private to that module")
    void internalPackagesAreNotSharedAcrossModules() {
        for (String module : new String[] {
            "tenant", "growth", "lead", "frontdesk", "appointment", "work", "content",
            "reporting", "integration", "eventing"
        }) {
            ArchRule rule = classes()
                    .that()
                    .resideInAPackage(ROOT + "." + module + ".internal..")
                    .should()
                    .onlyBeAccessed()
                    .byAnyPackage(ROOT + "." + module + "..")
                    .allowEmptyShould(true);
            rule.check(classes);
        }
    }

    @Test
    @DisplayName("no module reaches into another module's repositories")
    void repositoriesAreNotSharedAcrossModules() {
        for (String module : new String[] {"tenant", "eventing"}) {
            ArchRule rule = classes()
                    .that()
                    .resideInAPackage(ROOT + "." + module + ".repository..")
                    .should()
                    .onlyBeAccessed()
                    .byAnyPackage(ROOT + "." + module + "..", ROOT + ".audit..")
                    .allowEmptyShould(true);
            rule.check(classes);
        }
    }

    @Test
    @DisplayName("controllers do not return JPA entities")
    void controllersDoNotExposeEntities() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage(ROOT + "..web..")
                .should()
                .dependOnClassesThat()
                .areAnnotatedWith(jakarta.persistence.Entity.class)
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    @DisplayName("no module depends on a message broker")
    void nothingDependsOnABroker() {
        // Postgres is the queue: outbox_event on the way out, inbox_event on the way in. This test
        // is what stops a broker reappearing by accident, one import at a time.
        ArchRule rule = noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.kafka..", "org.apache.kafka..")
                .allowEmptyShould(true);
        rule.check(classes);
    }
}
