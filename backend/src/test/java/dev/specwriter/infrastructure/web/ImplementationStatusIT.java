package dev.specwriter.infrastructure.web;

import static dev.specwriter.support.LocalApi.api;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T054 : statut d'implémentation et verrou par l'API (FR-026 à FR-026b de 002). */
@QuarkusTest
class ImplementationStatusIT {

    @Test
    void statutVerrouEtEvolution() {
        SessionResourceIT.copilotConsent(true);
        JsonPath created = SessionResourceIT.create("IT-OK statutverrou");
        String id = created.getString("id");
        String question = created.getString("currentBatch[0].id");
        api().get("/api/sessions/" + id).then().statusCode(200).body("implementation.status", equalTo("DRAFT"));

        api().body(Map.of("status", "IMPLEMENTED")).put("/api/sessions/" + id + "/status").then().statusCode(200)
                .body("status", equalTo("IMPLEMENTED")).body("implementedAt", notNullValue());
        api().get("/api/sessions").then().statusCode(200)
                .body("find { it.id == '" + id + "' }.implementationStatus", equalTo("IMPLEMENTED"));
        api().body(Map.of("skip", true)).put("/api/sessions/" + id + "/questions/" + question + "/answer").then()
                .statusCode(409).body("code", equalTo("SPECIFICATION_LOCKED"));
        api().post("/api/sessions/" + id + "/conclude").then().statusCode(409).body("code", equalTo("SPECIFICATION_LOCKED"));

        api().body(Map.of("status", "DRAFT")).put("/api/sessions/" + id + "/status").then().statusCode(409)
                .body("code", equalTo("UNLOCK_CONFIRMATION_REQUIRED"));
        api().body(Map.of("status", "BIDON")).put("/api/sessions/" + id + "/status").then().statusCode(422);
        api().body(Map.of("status", "READY", "confirmUnlock", true)).put("/api/sessions/" + id + "/status").then()
                .statusCode(200).body("status", equalTo("READY")).body("implementedAt", nullValue());

        // sans version, pas d'évolution possible
        api().post("/api/sessions/" + id + "/evolutions").then().statusCode(422);
        api().delete("/api/sessions/" + id).then().statusCode(204);
        api().get("/api/sessions").then().statusCode(200).body("id", org.hamcrest.Matchers.not(hasItem(id)));
    }
}
