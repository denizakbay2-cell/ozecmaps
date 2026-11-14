package com.citynavigator.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * The structure decided in ADR-0001, checked on every build.
 */
@AnalyzeClasses(packages = "com.citynavigator", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerRulesTest {

    @ArchTest
    static final ArchRule layers_are_called_from_top_to_bottom = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Resource").definedBy("..resource..")
            .layer("Service").definedBy("..service..")
            .layer("Repository").definedBy("..repository..")
            .layer("Entity").definedBy("..entity..")
            .layer("Dto").definedBy("..dto..")
            .whereLayer("Resource").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Resource")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
            .whereLayer("Entity").mayOnlyBeAccessedByLayers("Service", "Repository")
            .whereLayer("Dto").mayOnlyBeAccessedByLayers("Resource", "Service");

    @ArchTest
    static final ArchRule the_routing_engine_is_plain_java = classes()
            .that().resideInAPackage("..routing..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("..routing..", "java..");

    @ArchTest
    static final ArchRule the_osm_reader_needs_only_routing_and_json = classes()
            .that().resideInAPackage("..osm..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("..osm..", "..routing..", "jakarta.json..", "java..");

    @ArchTest
    static final ArchRule the_service_clients_need_only_routing_json_and_http = classes()
            .that().resideInAPackage("..external..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("..external..", "..routing..", "jakarta.json..", "java..");

    @ArchTest
    static final ArchRule the_offline_tools_are_used_by_nothing = noClasses()
            .that().resideOutsideOfPackage("..tools..")
            .should().dependOnClassesThat().resideInAPackage("..tools..");

    @ArchTest
    static final ArchRule the_offline_tools_need_only_routing_and_osm = classes()
            .that().resideInAPackage("..tools..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("..tools..", "..osm..", "..routing..", "java..");

    @ArchTest
    static final ArchRule only_services_call_the_public_services = noClasses()
            .that().resideOutsideOfPackages("..service..", "..external..")
            .should().dependOnClassesThat().resideInAPackage("..external..");

    @ArchTest
    static final ArchRule only_repositories_use_the_entity_manager = noClasses()
            .that().resideOutsideOfPackage("..repository..")
            .should().dependOnClassesThat().areAssignableTo(EntityManager.class);

    @ArchTest
    static final ArchRule only_repositories_and_startup_use_jdbc = noClasses()
            .that().resideOutsideOfPackages("..repository..", "com.citynavigator")
            .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "javax.sql..");

    @ArchTest
    static final ArchRule transactional_classes_are_services = classes()
            .that().areAnnotatedWith(Transactional.class)
            .should().resideInAPackage("..service..");

    @ArchTest
    static final ArchRule transactional_methods_are_in_services = methods()
            .that().areAnnotatedWith(Transactional.class)
            .should().beDeclaredInClassesThat().resideInAPackage("..service..");

    @ArchTest
    static final ArchRule exceptions_know_nothing_about_other_layers = noClasses()
            .that().resideInAPackage("..exception..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..resource..", "..service..", "..repository..", "..entity..", "..dto..");

    @ArchTest
    static final ArchRule entities_do_not_know_http_dtos_or_the_engine = noClasses()
            .that().resideInAPackage("..entity..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.ws.rs..", "..dto..", "..routing..");
}
