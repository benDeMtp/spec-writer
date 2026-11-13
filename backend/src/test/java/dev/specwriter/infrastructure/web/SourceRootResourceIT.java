package dev.specwriter.infrastructure.web;

import static dev.specwriter.support.LocalApi.api;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import dev.specwriter.infrastructure.sources.DemoTree;
import io.quarkus.test.junit.QuarkusTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T013 : {@code /api/projects/{id}/source-roots} (013, contracts/api.md). */
@QuarkusTest
class SourceRootResourceIT {

    @TempDir
    Path dir;

    String projectId;
    String base;

    @BeforeEach
    void setUp() {
        projectId = api().body(Map.of("name", "Sources " + UUID.randomUUID())).post("/api/projects").then().statusCode(201)
                .extract().path("id");
        base = "/api/projects/" + projectId + "/source-roots";
    }

    @AfterEach
    void deleteProject() {
        api().delete("/api/projects/" + projectId + "?sessions=DELETE").then().statusCode(204);
    }

    @Test
    void racinesDeclareesModifieesSupprimeesEtVisiblesDansLeProjet() throws Exception {
        Path rv = Files.createDirectory(dir.resolve("rv"));
        String id = api().body(Map.of("location", rv.toString(), "exclusions", List.of("legacy/**"))).post(base).then()
                .statusCode(201).body("label", equalTo("rv")).body("state", equalTo("OK"))
                .body("exclusions", equalTo(List.of("legacy/**"))).extract().path("id");
        api().get(base).then().statusCode(200).body("$", hasSize(1));
        api().get("/api/projects").then().body("find { it.id == '" + projectId + "' }.sourceRoots[0].id", equalTo(id));
        api().body(Map.of("exclusions", List.of("*.sql"))).put(base + "/" + id + "/exclusions").then().statusCode(200)
                .body("exclusions", equalTo(List.of("*.sql")));
        Files.delete(rv);
        api().get(base).then().body("[0].state", equalTo("MISSING"));
        api().delete(base + "/" + id).then().statusCode(204);
        api().delete(base + "/" + id).then().statusCode(404);
        api().get(base).then().body("$", hasSize(0));
    }

    @Test
    void lesRacinesInvalidesSontRefuseesAvecLeurRaison() throws Exception {
        Path rv = Files.createDirectories(dir.resolve("rv/modules"));
        api().body(Map.of("location", dir.resolve("absent").toString())).post(base).then().statusCode(422)
                .body("userMessage", containsString("n'existe pas"));
        api().body(Map.of("location", rv.getParent().toString())).post(base).then().statusCode(201);
        api().body(Map.of("location", rv.getParent().toString())).post(base).then().statusCode(422)
                .body("userMessage", containsString("déjà"));
        api().body(Map.of("location", rv.toString())).post(base).then().statusCode(422);
        api().body(Map.of("location", dir.toString(), "exclusions", List.of("/abs"))).post(base).then().statusCode(422)
                .body("userMessage", containsString("/abs"));
        for (int i = 0; i < 9; i++) {
            api().body(Map.of("location", Files.createDirectory(dir.resolve("r" + i)).toString())).post(base).then()
                    .statusCode(201);
        }
        api().body(Map.of("location", Files.createDirectory(dir.resolve("r10")).toString())).post(base).then()
                .statusCode(422).body("userMessage", containsString("10"));
        api().get("/api/projects/" + UUID.randomUUID() + "/source-roots").then().statusCode(404);
    }

    @Test
    void lApercuDeLArbreDeDemonstration() {
        Path demo = DemoTree.copy(dir);
        api().body(Map.of("location", demo.toString(), "exclusions", List.of("legacy/**"))).post(base + "/preview").then()
                .statusCode(200).body("moduleCount", equalTo(13)).body("sensitiveFiles", equalTo(2))
                .body("modules.name", containsInAnyOrder("aggregates", "super", "bom", "common-contract",
                        "quarkus-contract", "spring-utils", "facturation", "relance", "annuaire", "batch-suspension",
                        "notification", "demo-front", "facturation-chart"))
                .body("excludedFiles", equalTo(1));
        api().get(base).then().body("$", hasSize(0));
    }
}
