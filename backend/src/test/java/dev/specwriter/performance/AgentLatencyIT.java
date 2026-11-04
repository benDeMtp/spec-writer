package dev.specwriter.performance;

import static dev.specwriter.support.LocalApi.api;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.project.Project;
import dev.specwriter.infrastructure.platform.AgentEndpointFile;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** T060, SC-007 de 002 : un agent obtient la liste des features puis le contenu d'une feature en moins de 5 s. */
@QuarkusTest
class AgentLatencyIT {

    static final long LIMIT_MS = 5_000;

    @AfterEach
    void disable() {
        api().body(Map.of("enabled", false)).put("/api/settings/agent-access").then().statusCode(204);
    }

    @Test
    void listePuisContenuEnMoinsDeCinqSecondes() throws Exception {
        api().body(Map.of("granted", true)).put("/api/consents/COPILOT").then().statusCode(204);
        String id = null;
        for (int i = 0; i < 20; i++) {
            JsonPath created = api().body(Map.of("need", "Les employés doivent pouvoir réserver une salle de réunion agentperf IT-OK " + i))
                    .post("/api/sessions").then().statusCode(201).extract().jsonPath();
            id = created.getString("id");
            for (String q : created.<String>getList("currentBatch.id")) {
                api().body(Map.of("skip", true)).put("/api/sessions/" + id + "/questions/" + q + "/answer").then().statusCode(200);
            }
            api().post("/api/sessions/" + id + "/conclude").then().statusCode(200);
            api().post("/api/sessions/" + id + "/versions").then().statusCode(201);
            api().body(Map.of("status", "READY")).put("/api/sessions/" + id + "/status").then().statusCode(200);
        }
        api().body(Map.of("enabled", true)).put("/api/settings/agent-access").then().statusCode(204);
        Path file = Path.of(ConfigProvider.getConfig().getValue("specwriter.data-dir.resolved", String.class))
                .resolve(AgentEndpointFile.FILE);
        String secret = new JsonPath(Files.readString(file)).getString("secret");

        long start = System.nanoTime();
        List<String> features = given().header("Authorization", "Bearer " + secret)
                .get("/api/agent/projects/" + Project.DEFAULT_ID + "/features").then().statusCode(200).extract().jsonPath()
                .getList("id");
        given().header("Authorization", "Bearer " + secret).get("/api/agent/features/" + id).then().statusCode(200);
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        assertThat(features).contains(id);
        assertThat(elapsed).as("liste puis contenu, en ms").isLessThan(LIMIT_MS);
    }
}
