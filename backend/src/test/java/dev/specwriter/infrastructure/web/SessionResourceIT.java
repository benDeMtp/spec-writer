package dev.specwriter.infrastructure.web;

import static dev.specwriter.support.LocalApi.api;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contrat de {@code /api/sessions} et {@code /api/sessions/{id}/versions} (contracts/openapi.yaml),
 * avec le LLM rejoué depuis {@code src/test/resources/llm-replay}.
 */
@QuarkusTest
class SessionResourceIT {

    static final String NEED = "Les employés doivent pouvoir réserver une salle de réunion pour une durée donnée.";

    @BeforeEach
    void consent() {
        copilotConsent(true);
    }

    static void copilotConsent(boolean granted) {
        api().body(Map.of("granted", granted)).put("/api/consents/COPILOT").then().statusCode(204);
    }

    static JsonPath create(String marker) {
        return api().body(Map.of("need", NEED + " " + marker)).post("/api/sessions").then().statusCode(201)
                .extract().jsonPath();
    }

    @Test
    void parcoursCompletDuBesoinALaSpecification() {
        JsonPath created = create("IT-OK");
        String id = created.getString("id");
        List<String> questions = created.getList("currentBatch.id");
        api().get("/api/sessions/" + id).then().statusCode(200)
                .body("state", equalTo("QUESTIONING"))
                .body("language", equalTo("fr"))
                .body("name", notNullValue())
                .body("latestVersion", nullValue())
                .body("currentBatch", hasSize(2))
                .body("currentBatch[0].rationale", equalTo("Pour cadrer la spécification"))
                .body("currentBatch[0].suggestions", hasItem("Gagner du temps"))
                .body("currentBatch[0].defaultValue", equalTo("Éviter les doublons"))
                .body("progress.total", equalTo(2));

        api().body(Map.of("value", "Gagner du temps")).put("/api/sessions/" + id + "/questions/" + questions.get(0) + "/answer")
                .then().statusCode(200).body("progress.answered", equalTo(1)).body("currentBatch", hasSize(1));
        api().body(Map.of("skip", true)).put("/api/sessions/" + id + "/questions/" + questions.get(1) + "/answer")
                .then().statusCode(200).body("progress.skipped", equalTo(1))
                .body("answeredQuestions.find { it.state == 'SKIPPED' }.answer", equalTo("Non précisé"));

        api().post("/api/sessions/" + id + "/conclude").then().statusCode(200)
                .body("state", equalTo("READY_TO_GENERATE"))
                .body("warnings[0]", containsString("hypothèses"));

        api().post("/api/sessions/" + id + "/versions").then().statusCode(201)
                .body("number", equalTo(1))
                .body("useCases[0].code", equalTo("UC-01"))
                .body("scenarios.code", equalTo(List.of("SC-01-01", "SC-01-02")))
                .body("assumptions[0].origin", equalTo("SKIPPED_QUESTION"))
                .body("requirements[0].code", equalTo("EF-01"))
                .body("markdown", containsString("- **En tant que** employé connecté"));
        // entrées identiques : version réutilisée
        api().post("/api/sessions/" + id + "/versions").then().statusCode(200).body("number", equalTo(1));
        api().get("/api/sessions/" + id + "/versions").then().statusCode(200).body("number", equalTo(List.of(1)));
        api().get("/api/sessions/" + id + "/versions/1").then().statusCode(200).body("title", equalTo("Réservation de salles"));
        api().get("/api/sessions/" + id + "/versions/2").then().statusCode(404);
        api().get("/api/sessions/" + id).then().body("state", equalTo("GENERATED")).body("latestVersion", equalTo(1));
        api().get("/api/sessions").then().statusCode(200).body("id", hasItem(id));
    }

    @Test
    void besoinTropCourtOuTropLong() {
        api().body(Map.of("need", "trop court")).post("/api/sessions").then().statusCode(422)
                .body("code", equalTo("NEED_TOO_SHORT")).body("userMessage", containsString("Exemple"));
        api().body(Map.of("need", NEED + " " + "x".repeat(10_000))).post("/api/sessions").then().statusCode(422)
                .body("code", equalTo("NEED_TOO_LONG"));
    }

    @Test
    void sansConsentementCopilot428() {
        copilotConsent(false);
        api().body(Map.of("need", NEED + " IT-OK")).post("/api/sessions").then().statusCode(428)
                .body("code", equalTo("CONSENT_REQUIRED")).body("action", equalTo("OPEN_SETTINGS"));
    }

    @Test
    void copilotIndisponible503() {
        api().body(Map.of("need", NEED + " IT-DOWN")).post("/api/sessions").then().statusCode(503)
                .body("code", equalTo("LLM_UNAVAILABLE")).body("action", equalTo("RETRY"));
    }

    @Test
    void sortieInvalide502SansVersion() {
        String id = create("IT-INVALID").getString("id");
        api().get("/api/sessions/" + id).then().body("state", equalTo("READY_TO_GENERATE"));
        api().post("/api/sessions/" + id + "/versions").then().statusCode(502).body("code", equalTo("LLM_INVALID_OUTPUT"));
        api().get("/api/sessions/" + id + "/versions").then().body("$", hasSize(0));
    }

    @Test
    void reponseSansValeurNiSkipEtIdentifiantsInconnus() {
        JsonPath created = create("IT-OK");
        String id = created.getString("id");
        String q = created.getString("currentBatch[0].id");
        api().body("{}").put("/api/sessions/" + id + "/questions/" + q + "/answer").then().statusCode(422);
        api().body(Map.of("value", "x".repeat(5001))).put("/api/sessions/" + id + "/questions/" + q + "/answer")
                .then().statusCode(422);
        api().get("/api/sessions/pas-un-uuid").then().statusCode(404);
        api().get("/api/sessions/00000000-0000-0000-0000-000000000000").then().statusCode(404);
        api().get("/api/sessions/00000000-0000-0000-0000-000000000000/versions").then().statusCode(404);
    }
}
