package nl.banksim.domain;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Bewaakt TO §5: geen floating point voor geld en geen frameworkafhankelijkheden in het domein. */
@AnalyzeClasses(packages = "nl.banksim.domain", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectuurTests {

    @ArchTest
    static final ArchRule geenFloatingPointVelden = fields().should().notHaveRawType(double.class)
            .andShould().notHaveRawType(float.class)
            .andShould().notHaveRawType(Double.class)
            .andShould().notHaveRawType(Float.class)
            .because("geld wordt altijd met BigDecimal berekend");

    @ArchTest
    static final ArchRule geenFloatingPointReturntypes = methods().should().notHaveRawReturnType(double.class)
            .andShould().notHaveRawReturnType(float.class)
            .andShould().notHaveRawReturnType(Double.class)
            .andShould().notHaveRawReturnType(Float.class);

    @ArchTest
    static final ArchRule geenFloatingPointParameters = noClasses().should()
            .callConstructor(java.math.BigDecimal.class, double.class)
            .orShould().callMethod(java.math.BigDecimal.class, "valueOf", double.class)
            .because("new BigDecimal(double) en BigDecimal.valueOf(double) introduceren afrondingsfouten");

    @ArchTest
    static final ArchRule geenFrameworks = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..", "org.hibernate..")
            .because("het domein is vrij van frameworks");
}
