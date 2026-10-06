package nl.banksim.datagen;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Ook de generator rekent nooit met floating point (TO §5). */
@AnalyzeClasses(packages = "nl.banksim.datagen", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectuurTests {

    @ArchTest
    static final ArchRule geenFloatingPointVelden = fields().should().notHaveRawType(double.class)
            .andShould().notHaveRawType(float.class);

    @ArchTest
    static final ArchRule geenFloatingPointToeval = noClasses().should()
            .callMethod(java.util.Random.class, "nextDouble")
            .orShould().callMethod(java.util.Random.class, "nextFloat")
            .orShould().callMethod(java.util.Random.class, "nextGaussian")
            .orShould().callConstructor(java.math.BigDecimal.class, double.class);
}
