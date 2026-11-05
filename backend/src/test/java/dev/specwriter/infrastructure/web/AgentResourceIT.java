package dev.specwriter.infrastructure.web;

import static dev.specwriter.support.LocalApi.api;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import dev.specwriter.domain.project.Project;
import dev.specwriter.infrastructure.platform.AgentEndpointFile;
import dev.specwriter.support.LocalApi;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** T050 : API des agents (contracts/mcp-tools.md), journal, et refus (SC-007, SC-008). */
@QuarkusTest
class AgentResourceIT {

    static final String DEFAULT = Project.DEFAULT_ID.toString();

    static Path endpointFile() {
        return Path.of(ConfigProvider.getConfig().getValue("specwriter.data-dir.resolved", String.class))
                .resolve(AgentEndpointFile.FILE);
    }

    static String secret() throws Exception {
        return new JsonPath(Files.readString(endpointFile())).getString("secret");
    }

    static RequestSpecification agent(String secret) {
        return given().header("Authorization", "Bearer " + secret).header("X-Agent-Name", "Claude Code")
                .contentType(ContentType.JSON);
    }

    static String generated(String marker) {
        JsonPath created = SessionResourceIT.create("IT-OK " + marker);
        String id = created.getString("id");
        for (String q : created.<String>getList("currentBatch.id")) {
            api().body(Map.of("skip", true)).put("/api/sessions/" + id + "/questions/" + q + "/answer").then().statusCode(200);
        }
        api().post("/api/sessions/" + id + "/conclude").then().statusCode(200);
        api().post("/api/sessions/" + id + "/versions").then().statusCode(201);
        return id;
    }

    @AfterEach
    void disable() {
        api().body(Map.of("enabled", false)).put("/api/settings/agent-access").then().statusCode(204);
    }

    @Test
    void contratDesOutilsEtJournal() throws Exception {
        SessionResourceIT.copilotConsent(true);
        String ready = generated("agentpret");
        String draft = generated("agentbrouillon");
        api().body(Map.of("status", "READY")).put("/api/sessions/" + ready + "/status").then().statusCode(200);

        api().get("/api/settings/agent-access").then().statusCode(200).body("enabled", equalTo(false));
        assertThat(endpointFile()).doesNotExist();
        api().body(Map.of("enabled", true)).put("/api/settings/agent-access").then().statusCode(204);
        api().get("/api/settings/agent-access").then().statusCode(200).body("enabled", equalTo(true))
                .body("mcpJson", org.hamcrest.Matchers.containsString("dev.specwriter.mcp.McpBridge"));
        String secret = secret();
        assertThat(api().get("/api/settings/agent-access").asString()).doesNotContain(secret);
        int logged = api().get("/api/settings/agent-access/log").then().extract().jsonPath().getInt("total");

        agent(secret).get("/api/agent/projects").then().statusCode(200).body("id", hasItem(DEFAULT))
                .body("[0]", not(org.hamcrest.Matchers.hasKey("context")));
        agent(secret).get("/api/agent/projects/" + DEFAULT + "/features").then().statusCode(200)
                .body("id", hasItem(ready)).body("id", not(hasItem(draft)));
        String export = api().get("/api/sessions/" + ready + "/versions/1/export").asString();
        agent(secret).get("/api/agent/features/" + ready).then().statusCode(200).body("markdown", equalTo(export))
                .body("status", equalTo("READY")).body("version", equalTo(1));
        agent(secret).get("/api/agent/features/" + draft).then().statusCode(404);

        // recherche locale, non consignée
        List<Object> hits = agent(secret).queryParam("q", "salle").get("/api/agent/projects/" + DEFAULT + "/knowledge")
                .then().statusCode(200).extract().jsonPath().getList("$");
        assertThat(hits.size()).isLessThanOrEqualTo(10);

        agent(secret).body(Map.of("status", "IN_PROGRESS")).put("/api/agent/features/" + ready + "/status").then()
                .statusCode(200).body("status", equalTo("IN_PROGRESS"));
        agent(secret).body(Map.of("status", "READY")).put("/api/agent/features/" + ready + "/status").then()
                .statusCode(409).body("code", equalTo("TRANSITION_NOT_ALLOWED"));
        agent(secret).body(Map.of("status", "IMPLEMENTED")).put("/api/agent/features/" + ready + "/status").then()
                .statusCode(200);
        agent(secret).get("/api/agent/features/" + ready).then().statusCode(404);

        JsonPath log = api().get("/api/settings/agent-access/log").then().statusCode(200).extract().jsonPath();
        // une feature introuvable n'est pas consignée : aucune feature à nommer
        assertThat(log.getInt("total")).isEqualTo(logged + 5);
        assertThat(log.<String>getList("entries.operation").subList(0, 5)).containsExactly("SET_STATUS", "SET_STATUS",
                "GET_FEATURE", "LIST_FEATURES", "LIST_PROJECTS");
        assertThat(log.getString("entries[0].agentName")).isEqualTo("Claude Code");
        assertThat(log.getString("entries[0].oldStatus")).isEqualTo("IN_PROGRESS");
        assertThat(log.getString("entries[0].newStatus")).isEqualTo("IMPLEMENTED");
        assertThat(log.getString("entries[0].featureTitle"))
                .isEqualTo(api().get("/api/sessions/" + ready).jsonPath().getString("name"));
        assertThat(log.prettify()).doesNotContain(export.lines().skip(2).findFirst().orElse("#"));
    }

    @Test
    void refus() throws Exception {
        api().body(Map.of("enabled", true)).put("/api/settings/agent-access").then().statusCode(204);
        String old = secret();
        given().get("/api/agent/projects").then().statusCode(401).body("code", equalTo("AGENT_ACCESS_DENIED"));
        agent("faux").get("/api/agent/projects").then().statusCode(401);
        agent(old).header("Origin", LocalApi.origin()).get("/api/agent/projects").then().statusCode(403);
        given().cookie("sw_launch", LocalApi.cookie()).get("/api/agent/projects").then().statusCode(401);
        // le secret n'ouvre pas le reste de l'API
        agent(old).get("/api/sessions").then().statusCode(403);
        agent(old).header("Host", "evil.example:80").get("/api/agent/projects").then().statusCode(403);

        api().post("/api/settings/agent-access/secret").then().statusCode(204);
        String renewed = secret();
        assertThat(renewed).isNotEqualTo(old);
        agent(old).get("/api/agent/projects").then().statusCode(401);
        agent(renewed).get("/api/agent/projects").then().statusCode(200);

        api().body(Map.of("enabled", false)).put("/api/settings/agent-access").then().statusCode(204);
        assertThat(endpointFile()).doesNotExist();
        agent(renewed).get("/api/agent/projects").then().statusCode(401);
        api().post("/api/settings/agent-access/secret").then().statusCode(422);
    }
}
