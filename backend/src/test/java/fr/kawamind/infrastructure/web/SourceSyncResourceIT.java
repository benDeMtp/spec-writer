package fr.kawamind.infrastructure.web;

import static fr.kawamind.support.LocalApi.api;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T029 : {@code /api/projects/{id}/source-sync} (017, contracts/rest-api.md). */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class SourceSyncResourceIT {

    String project;
    Path folder;

    @BeforeEach
    void projet() throws Exception {
        project = api().body(Map.of("name", "Délais " + System.nanoTime())).post("/api/projects").then().statusCode(201)
                .extract().path("id");
        folder = Files.createTempDirectory("source-sync-api");
    }

    @AfterEach
    void nettoyer() throws Exception {
        api().delete("/api/projects/" + project + "?sessions=DELETE");
        Files.deleteIfExists(folder);
    }

    @Test
    void delaiDUneSourceEtBilan() {
        String doc = api().body(Map.of("kind", "DOCUMENTS", "location", folder.toString()))
                .post("/api/projects/" + project + "/doc-sources").then().statusCode(201).extract().path("id");
        String base = "/api/projects/" + project + "/source-sync";
        api().get(base).then().statusCode(200).body("sourceKey", hasItem(doc)).body("find { it.sourceKey == '" + doc
                + "' }.interval", equalTo("OFF"));
        api().body(Map.of("intervalMinutes", 240)).put(base + "/" + doc).then().statusCode(200)
                .body("interval", equalTo("H4")).body("kind", equalTo("DOCUMENTATION"));
        Map<String, Object> off = new HashMap<>();
        off.put("intervalMinutes", null);
        api().body(off).put(base + "/" + doc).then().statusCode(200).body("interval", equalTo("OFF"))
                .body("nextRunAt", nullValue());
        api().body(Map.of("intervalMinutes", 20)).put(base + "/" + doc).then().statusCode(422);
        api().body(Map.of("intervalMinutes", 15)).put(base + "/inconnue").then().statusCode(404);
        given().contentType("application/json").body(Map.of("intervalMinutes", 15)).put(base + "/" + doc).then()
                .statusCode(403);
    }
}
