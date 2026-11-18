package fr.kawamind.infrastructure.web;

import static fr.kawamind.support.LocalApi.api;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T043 (016) : {@code /api/projects/{id}/reliability}, niveau propre d'une racine
 * ({@code …/doc-sources/{sourceId}/reliability}), aperçu Confluence sans serveur déclaré.
 */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class ReliabilityResourceIT {

    String projectId;

    @BeforeEach
    void setUp() {
        projectId = api().body(Map.of("name", "Fiabilité " + UUID.randomUUID())).post("/api/projects").then()
                .statusCode(201).extract().path("id");
    }

    @AfterEach
    void deleteProject() {
        api().delete("/api/projects/" + projectId + "/indexations/current").then().statusCode(204);
        api().delete("/api/projects/" + projectId + "?sessions=DELETE").then().statusCode(204);
    }

    @Test
    void niveauxParDefautPuisReglage() {
        api().get("/api/projects/" + projectId + "/reliability").then().statusCode(200)
                .body("levels.DOCUMENTS", is("REFERENCE")).body("levels.CONFLUENCE", is("INTERPRETATION"))
                .body("defaults.JIRA", is("INTERPRETATION"));
        api().body(Map.of("origin", "CONFLUENCE", "level", "TO_CONFIRM")).put("/api/projects/" + projectId
                + "/reliability").then().statusCode(200).body("levels.CONFLUENCE", is("TO_CONFIRM"))
                .body("defaults.CONFLUENCE", is("INTERPRETATION"));
        api().get("/api/projects/" + projectId + "/reliability").then().body("levels.CONFLUENCE", is("TO_CONFIRM"));
        api().body(Map.of("origin", "SHEET", "level", "REFERENCE")).put("/api/projects/" + projectId + "/reliability")
                .then().statusCode(422);
        api().body(Map.of("origin", "JIRA", "level", "HAUTE")).put("/api/projects/" + projectId + "/reliability")
                .then().statusCode(422);
    }

    @Test
    void niveauProprePasHorsConfluence() {
        String sourceId = api().body(Map.of("kind", "URL", "location", "https://doc.example.com/"))
                .post("/api/projects/" + projectId + "/doc-sources").then().statusCode(201).extract().path("id");
        Map<String, Object> body = new HashMap<>();
        body.put("reliability", "REFERENCE");
        api().body(body).put("/api/projects/" + projectId + "/doc-sources/" + sourceId + "/reliability").then()
                .statusCode(422).body("userMessage", containsString("Confluence"));
    }

    @Test
    void racineConfluenceSansServeurDeclare() {
        api().delete("/api/settings/confluence").then().statusCode(204);
        api().body(Map.of("kind", "CONFLUENCE", "location", "https://wiki.example.com/pages/viewpage.action?pageId=1"))
                .post("/api/projects/" + projectId + "/doc-sources/preview").then().statusCode(200)
                .body("problem", is("CONFLUENCE_NOT_CONFIGURED"));
        api().body(Map.of("kind", "CONFLUENCE", "location", "https://wiki.example.com/pages/viewpage.action?pageId=1"))
                .post("/api/projects/" + projectId + "/doc-sources").then().statusCode(409)
                .body("code", is("CONFLUENCE_NOT_CONFIGURED")).body("action", is("OPEN_SETTINGS"));
    }
}
