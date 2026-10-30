package dev.specwriter.infrastructure.web;

import static dev.specwriter.support.LocalApi.api;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Gestion des sessions et réponse modifiée (FR-007, FR-017, FR-018), LLM rejoué. */
@QuarkusTest
class SessionManagementIT {

    @BeforeEach
    void consent() {
        SessionResourceIT.copilotConsent(true);
    }

    @Test
    void listerRenommerSupprimer() {
        String id = SessionResourceIT.create("IT-OK").getString("id");
        api().get("/api/sessions").then().statusCode(200)
                .body("find { it.id == '" + id + "' }.readable", equalTo(true))
                .body("find { it.id == '" + id + "' }.state", equalTo("QUESTIONING"));

        api().body(Map.of("name", "Salles du siège")).patch("/api/sessions/" + id).then().statusCode(200)
                .body("name", equalTo("Salles du siège"));
        api().get("/api/sessions").then().body("[0].id", equalTo(id)).body("[0].name", equalTo("Salles du siège"));
        api().body(Map.of("name", "x".repeat(121))).patch("/api/sessions/" + id).then().statusCode(422);

        api().get("/api/sessions/" + id + "/versions").then().statusCode(200).body("$", hasSize(0));
        api().delete("/api/sessions/" + id).then().statusCode(204);
        api().get("/api/sessions/" + id).then().statusCode(404);
        api().get("/api/sessions").then().body("id", not(hasItem(id)));
        api().delete("/api/sessions/" + id).then().statusCode(404);
    }

    @Test
    void reponseDejaDonneeModifieeEtRegenerationEnVersionDeux() {
        JsonPath created = SessionResourceIT.create("IT-REVISE");
        String id = created.getString("id");
        List<String> q = created.getList("currentBatch.id");
        api().body(Map.of("value", "Gagner du temps")).put("/api/sessions/" + id + "/questions/" + q.get(0) + "/answer")
                .then().statusCode(200);
        api().body(Map.of("value", "Proposer une autre salle")).put("/api/sessions/" + id + "/questions/" + q.get(1) + "/answer")
                .then().statusCode(200).body("state", equalTo("READY_TO_GENERATE"));
        api().post("/api/sessions/" + id + "/versions").then().statusCode(201).body("number", equalTo(1));

        // modification : la question 2 devient sans objet, une nouvelle question est posée
        api().body(Map.of("value", "Éviter les doublons RÉVISÉ")).put("/api/sessions/" + id + "/questions/" + q.get(0) + "/answer")
                .then().statusCode(200)
                .body("state", equalTo("QUESTIONING"))
                .body("revision.removed", equalTo(List.of("Que se passe-t-il si la salle est déjà prise ?")))
                .body("revision.added", equalTo(List.of("Combien de personnes au maximum ?")))
                .body("currentBatch.label", equalTo(List.of("Combien de personnes au maximum ?")))
                .body("answeredQuestions.label", equalTo(List.of("Quel est l'objectif principal ?")));
        String added = api().get("/api/sessions/" + id).then().extract().path("currentBatch[0].id");
        api().body(Map.of("value", "12")).put("/api/sessions/" + id + "/questions/" + added + "/answer").then()
                .statusCode(200).body("state", equalTo("READY_TO_GENERATE"));

        api().post("/api/sessions/" + id + "/versions").then().statusCode(201).body("number", equalTo(2));
        api().get("/api/sessions/" + id + "/versions").then().body("number", equalTo(List.of(1, 2)))
                .body("origin", equalTo(List.of("GENERATED", "GENERATED")));
        api().get("/api/sessions/" + id + "/versions/1").then().statusCode(200)
                .body("markdown", containsString("Réservation"));
    }
}
