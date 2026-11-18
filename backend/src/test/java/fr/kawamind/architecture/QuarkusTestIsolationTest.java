package fr.kawamind.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Les tests unitaires tournent en parallèle sous Maven (pom, surefire) : un {@code @QuarkusTest}
 * partage l'application de test, sa base et ses ports, il doit donc tourner seul
 * ({@code @Isolated}). Sauf la suite d'évaluation, en série dans le profil eval.
 */
class QuarkusTestIsolationTest {

    private static final Pattern QUARKUS_TEST = Pattern.compile("(?m)^@(io\\.quarkus\\.test\\.junit\\.)?QuarkusTest\\s*$");

    @Test
    void chaqueTestQuarkusEstIsole() throws IOException {
        List<Path> missing;
        try (Stream<Path> files = Files.walk(Path.of("src/test/java"))) {
            // la suite d'évaluation (profil eval) tourne en série, et son code fait partie de
            // l'empreinte de last-run.json : elle n'a pas besoin de @Isolated
            missing = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.startsWith(Path.of("src/test/java/fr/kawamind/eval"))).filter(p -> {
                String s = read(p);
                return QUARKUS_TEST.matcher(s).find() && !s.contains("@org.junit.jupiter.api.parallel.Isolated")
                        && !s.contains("@Isolated");
            }).toList();
        }
        assertThat(missing).as("ajouter @org.junit.jupiter.api.parallel.Isolated sous @QuarkusTest").isEmpty();
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
