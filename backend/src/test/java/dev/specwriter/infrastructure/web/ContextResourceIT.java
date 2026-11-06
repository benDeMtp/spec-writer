package dev.specwriter.infrastructure.web;

import static dev.specwriter.support.LocalApi.api;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T025, T113 : {@code PUT /api/projects/{id}/context} (fiche générale) et {@code /doc-sources*}
 * (contracts/openapi.yaml de 002, US2) ; les endpoints des fiches générées n'existent plus (R28).
 */
@QuarkusTest
class ContextResourceIT {

    String projectId;

    @BeforeEach
    void setUp() {
        projectId = api().body(Map.of("name", "Contexte " + UUID.randomUUID())).post("/api/projects").then()
                .statusCode(201).body("context", equalTo("")).body("contextUpdatedAt", nullValue()).extract().path("id");
    }

    /** La base survit d'un test à l'autre : un projet laissé fausserait le projet courant de repli. */
    @AfterEach
    void deleteProject() {
        api().delete("/api/projects/" + projectId + "/indexations/current").then().statusCode(204);
        api().delete("/api/projects/" + projectId + "?sessions=DELETE").then().statusCode(204);
    }

    @Test
    void ficheGeneraleRedigeeALaMain() {
        api().body(Map.of("markdown", "  ## Acteurs\n- Le gestionnaire émet les avoirs.  ")).put("/api/projects/" + projectId
                + "/context").then().statusCode(200).body("context", equalTo("## Acteurs\n- Le gestionnaire émet les avoirs."))
                .body("contextUpdatedAt", notNullValue());
        String project = "find { it.id == '" + projectId + "' }";
        api().get("/api/projects").then().body(project + ".context", equalTo("## Acteurs\n- Le gestionnaire émet les avoirs."));
        // vidée, elle redevient « aucune fiche »
        api().body(Map.of("markdown", " ")).put("/api/projects/" + projectId + "/context").then().statusCode(200)
                .body("context", equalTo(""));
        api().body(Map.of("markdown", "x".repeat(20_001))).put("/api/projects/" + projectId + "/context").then()
                .statusCode(422);
        api().body(Map.of("markdown", "x")).put("/api/projects/" + UUID.randomUUID() + "/context").then().statusCode(404)
                .body("code", equalTo("PROJECT_NOT_FOUND"));
    }

    @Test
    void endpointsDesFichesGenereesRetires() {
        api().body(Map.of("markdown", "x")).put("/api/projects/" + projectId + "/context/functional").then()
                .statusCode(404);
        api().post("/api/projects/" + projectId + "/context-generations").then().statusCode(404);
        api().get("/api/projects/" + projectId + "/context-questions").then().statusCode(404);
        api().get("/api/projects/" + projectId + "/context-proposals/preview").then().statusCode(404);
    }

    @Test
    void sourcesAjouteesSupprimeesEtValidees() throws URISyntaxException {
        String folder = Path.of(ContextResourceIT.class.getResource("/antora-site").toURI()).toString();
        String id = api().body(Map.of("kind", "FOLDER", "location", folder)).post("/api/projects/" + projectId
                + "/doc-sources").then().statusCode(201).body("kind", equalTo("FOLDER")).body("location", equalTo(folder))
                .extract().path("id");
        api().body(Map.of("kind", "URL", "location", "https://moi:secret@docs.example.com/")).post("/api/projects/"
                + projectId + "/doc-sources").then().statusCode(422);
        api().body(Map.of("kind", "FTP", "location", "x")).post("/api/projects/" + projectId + "/doc-sources").then()
                .statusCode(422);
        api().delete("/api/projects/" + projectId + "/doc-sources/" + id).then().statusCode(204);
        api().delete("/api/projects/" + projectId + "/doc-sources/" + id).then().statusCode(404);
    }

    /** 006 : dossier de documents, motifs, aperçu ; motif refusé avec le motif cité. */
    @Test
    void dossierDeDocumentsMotifsEtApercu(@org.junit.jupiter.api.io.TempDir Path tmp) throws java.io.IOException {
        Path folder = dev.specwriter.support.TestDocuments.writeAll(tmp.resolve("Aurora"));
        String base = "/api/projects/" + projectId + "/doc-sources";
        String id = api().body(Map.of("kind", "DOCUMENTS", "location", folder.toString(), "exclusions",
                java.util.List.of("Archives/**"))).post(base).then().statusCode(201).body("kind", equalTo("DOCUMENTS"))
                .body("exclusions", org.hamcrest.Matchers.contains("Archives/**")).extract().path("id");

        api().body(Map.of("location", folder.toString(), "exclusions", java.util.List.of("Archives/**", "*.xlsx")))
                .post(base + "/preview").then().statusCode(200).body("retained", equalTo(6)).body("excluded", equalTo(2))
                .body("ignored", equalTo(1)).body("excludedExamples", org.hamcrest.Matchers.contains("Archives/2023.pdf",
                        "Suivi.xlsx")).body("ignoredExamples", org.hamcrest.Matchers.contains("vieux.doc"))
                .body("limitReached", equalTo(false));

        api().body(Map.of("exclusions", java.util.List.of("*.pdf"))).put(base + "/" + id + "/exclusions").then()
                .statusCode(200).body("exclusions", org.hamcrest.Matchers.contains("*.pdf"));
        api().body(Map.of("exclusions", java.util.List.of("[abc"))).put(base + "/" + id + "/exclusions").then()
                .statusCode(422).body("userMessage", org.hamcrest.Matchers.containsString("[abc"));
        api().body(Map.of("kind", "FOLDER", "location", folder.toString(), "exclusions", java.util.List.of("*.pdf")))
                .post(base).then().statusCode(422);
        api().body(Map.of("exclusions", java.util.List.of())).put(base + "/" + UUID.randomUUID() + "/exclusions").then()
                .statusCode(404);
        api().body(Map.of("location", tmp.resolve("absent").toString())).post(base + "/preview").then().statusCode(422);
        api().get("/api/projects").then().body("find { it.id == '" + projectId + "' }.docSources[0].exclusions",
                org.hamcrest.Matchers.contains("*.pdf"));
    }
}
