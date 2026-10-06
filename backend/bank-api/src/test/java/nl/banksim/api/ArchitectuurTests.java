package nl.banksim.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** Bewaakt de afspraken uit TO §5, §6, §8, §10.3 en de modulegrenzen (Spring Modulith). */
class ArchitectuurTests {

    static final JavaClasses KLASSEN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("nl.banksim.api");

    @Test
    void modulegrenzenWordenGerespecteerd() {
        ApplicationModules.of(BankApiApplication.class).verify();
    }

    @Test
    void elkeEndpointHeeftEenExpliciteAutorisatie() {
        methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .and().arePublic()
                .should().beAnnotatedWith(PreAuthorize.class)
                .because("deny by default: elke endpoint noemt expliciet wie hem mag gebruiken (TO §10.3)")
                .check(KLASSEN);
    }

    @Test
    void geenFloatingPoint() {
        fields().should().notHaveRawType(double.class).andShould().notHaveRawType(float.class)
                .andShould().notHaveRawType(Double.class).andShould().notHaveRawType(Float.class)
                .because("geld wordt altijd met BigDecimal berekend (TO §5)")
                .check(KLASSEN.that(com.tngtech.archunit.base.DescribedPredicate.not(
                        com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage("nl.banksim.api.contract.."))));
    }

    @Test
    void deDatumKomtAlleenUitDeSimulatieklok() {
        noClasses().that().resideOutsideOfPackage("nl.banksim.api.simulation..")
                .should().callMethod(LocalDate.class, "now")
                .orShould().callMethod(LocalDateTime.class, "now")
                .orShould().callMethod(Instant.class, "now")
                .because("de simulatiedatum vervangt de systeemdatum (TO §8)")
                .check(KLASSEN);
    }

    @Test
    void alleenHetGrootboekSchrijftBoekingen() throws IOException {
        Path bron = Path.of("src/main/java/nl/banksim/api");
        List<Path> schrijvers;
        try (Stream<Path> bestanden = Files.walk(bron)) {
            schrijvers = bestanden.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> leesbaar(p).matches("(?s).*INSERT\\s+INTO\\s+(boeking|overboeking)\\b.*"))
                    .toList();
        }
        assertThat(schrijvers).as("alleen LedgerService boekt (TO §6)")
                .containsExactly(bron.resolve("ledger/LedgerService.java"));
    }

    private static String leesbaar(Path pad) {
        try {
            return Files.readString(pad);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
