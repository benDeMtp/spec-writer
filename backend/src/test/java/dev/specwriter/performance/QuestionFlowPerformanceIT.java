package dev.specwriter.performance;

import static dev.specwriter.support.LocalApi.api;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * T136, SC-007 : avec un LLM rejoué (latence nulle), 95 % des enchaînements « répondre, puis
 * question suivante du lot » prennent moins de 3 s. Le démarrage du jar jusqu'à
 * {@code /q/health/ready} est mesuré par l'E2E (startup.spec.ts), sur le vrai jar.
 */
@QuarkusTest
class QuestionFlowPerformanceIT {

    private static final String NEED = "Les employés doivent pouvoir réserver une salle de réunion pour une durée donnée. IT-OK";
    private static final int SESSIONS = 20;
    private static final long LIMIT_MS = 3_000;

    @Test
    void quatreVingtQuinzePourCentDesReponsesEnMoinsDeTroisSecondes() {
        api().body(Map.of("granted", true)).put("/api/consents/COPILOT").then().statusCode(204);
        List<Long> durations = new ArrayList<>();
        for (int s = 0; s < SESSIONS; s++) {
            JsonPath session = api().body(Map.of("need", NEED)).post("/api/sessions").then().statusCode(201).extract().jsonPath();
            String id = session.getString("id");
            List<String> batch = session.getList("currentBatch.id");
            for (String question : batch) {
                long start = System.nanoTime();
                api().body(Map.of("value", "Oui")).put("/api/sessions/" + id + "/questions/" + question + "/answer")
                        .then().statusCode(200);
                durations.add((System.nanoTime() - start) / 1_000_000);
            }
        }

        assertThat(durations).hasSizeGreaterThanOrEqualTo(SESSIONS);
        Collections.sort(durations);
        long p95 = durations.get((int) Math.ceil(durations.size() * 0.95) - 1);
        assertThat(p95).as("95e centile des réponses, en ms (%s)", durations).isLessThan(LIMIT_MS);
    }
}
