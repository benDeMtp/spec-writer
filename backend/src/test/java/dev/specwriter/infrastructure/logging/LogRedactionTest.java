package dev.specwriter.infrastructure.logging;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.support.LocalApi;
import io.quarkus.test.junit.QuarkusTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LogRedactionTest {

    private static final Path LOG_FILE = Path.of("target/test-logs/spec-writer.log");

    @Test
    void niContenuSaisiNiJetonDansLesJournaux() throws Exception {
        // Corps invalide contenant un marqueur de contenu utilisateur, et un jeton dans l'en-tête
        given().header("Origin", LocalApi.origin())
                .header("Authorization", "Bearer gho_TESTTOKEN1234567890")
                .contentType("application/json")
                .body("{\"launchToken\": \"SECRET-NEED-MARKER\", ")
                .post("/api/session-auth");
        LocalApi.api().header("Authorization", "Bearer gho_TESTTOKEN1234567890")
                .body("{\"need\": \"SECRET-NEED-MARKER\"").post("/api/sessions");
        // Un développeur qui journaliserait un jeton par erreur : le filtre doit le masquer.
        Logger.getLogger(LogRedactionTest.class).infof("appel avec %s", "Bearer gho_TESTTOKEN1234567890");

        String logs = Files.readString(LOG_FILE);
        assertThat(logs).doesNotContain("SECRET-NEED-MARKER").doesNotContain("gho_TESTTOKEN");
        assertThat(logs).contains(RedactingLogFilter.MASK);
    }

    @Test
    void motifsMasques() {
        assertThat(RedactingLogFilter.redact("x gho_abcdef123 y")).isEqualTo("x [MASQUÉ] y");
        assertThat(RedactingLogFilter.redact("token github_pat_ABCDEFGHIJ1234567890")).doesNotContain("github_pat_");
        assertThat(RedactingLogFilter.redact("Authorization: Bearer abc.def")).doesNotContain("abc.def");
        assertThat(RedactingLogFilter.redact("http://127.0.0.1:1/?launch=ABCDEFGHIJKL")).doesNotContain("ABCDEFGHIJKL");
    }
}
