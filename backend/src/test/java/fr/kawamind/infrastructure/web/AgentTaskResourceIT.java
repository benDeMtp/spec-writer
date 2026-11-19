package fr.kawamind.infrastructure.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static fr.kawamind.support.LocalApi.api;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import com.github.tomakehurst.wiremock.WireMockServer;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.port.LinkedTicketRepository;
import fr.kawamind.domain.port.TaskRepository;
import fr.kawamind.domain.task.Basis;
import fr.kawamind.domain.task.SpecTask;
import fr.kawamind.domain.task.TaskOrigin;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T041 (015, contracts/mcp-tools.md) : outils de tâches de l'API des agents, codes et statuts HTTP. */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class AgentTaskResourceIT {

    static WireMockServer jira;
    static final String ME = "100-" + ProcessHandle.current().info().startInstant().orElseThrow().toEpochMilli();

    @Inject
    LinkedTicketRepository tickets;
    @Inject
    TaskRepository tasks;

    String story;
    String key;

    @BeforeAll
    static void start() {
        jira = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        jira.start();
    }

    @AfterAll
    static void stop() {
        jira.stop();
    }

    @BeforeEach
    void setUp() {
        jira.resetAll();
        api().body(Map.of("serverUrl", "http://127.0.0.1:" + jira.port(), "defaultProjectKey", "PROJ", "acceptInsecureHttp",
                true, "token", "TEST-JIRA-PAT")).put("/api/settings/jira").then().statusCode(200);
        SearchAndJiraResourceIT.consent("JIRA", true);
        api().body(Map.of("enabled", true)).put("/api/settings/agent-access").then().statusCode(204);
        int n = 2000 + ThreadLocalRandom.current().nextInt(1_000_000);
        story = "PROJ-" + n;
        key = "PROJ-" + (n + 1);
        String category = "{\"name\":\"%s\",\"statusCategory\":{\"key\":\"%s\"}}";
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).inScenario("t").whenScenarioStateIs(STARTED)
                .willReturn(okJson(search(category.formatted("À faire", "new")))));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).inScenario("t").whenScenarioStateIs("pris")
                .willReturn(okJson(search(category.formatted("En cours", "indeterminate")))));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/" + key)).willReturn(okJson(
                "{\"key\":\"" + key + "\",\"fields\":{\"status\":" + category.formatted("À faire", "new")
                        + ",\"updated\":\"2026-11-17T09:00:00.000+0000\"}}")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/" + key + "/transitions")).willReturn(okJson("""
                {"transitions":[{"id":"21","name":"Démarrer","to":{"statusCategory":{"key":"indeterminate"}}}]}""")));
        jira.stubFor(post("/rest/api/2/issue/" + key + "/transitions").inScenario("t").willSetStateTo("pris")
                .willReturn(aResponse().withStatus(204)));
    }

    String search(String status) {
        return "{\"startAt\":0,\"total\":1,\"issues\":[{\"key\":\"" + key + "\",\"fields\":{\"summary\":\"T\",\"status\":"
                + status + ",\"issuetype\":{\"name\":\"Sous-tâche\"},\"updated\":\"2026-11-17T09:00:00.000+0000\"}}]}";
    }

    @AfterEach
    void disable() {
        api().body(Map.of("enabled", false)).put("/api/settings/agent-access").then().statusCode(204);
    }

    String feature() {
        String id = AgentResourceIT.generated("TACHES");
        UUID session = UUID.fromString(id);
        Instant t = Instant.parse("2026-11-17T09:00:00Z");
        tickets.save(new LinkedJiraTicket(session, story, "http://jira/browse/" + story, "Story", 1, t, "h", t, null,
                LinkedJiraTicket.SyncState.IN_SYNC));
        tasks.replaceAll(session, List.of(SpecTask.proposed(UUID.randomUUID(), session, 0, "Exposer l'endpoint",
                "POST renvoie 201", Basis.useCase("UC-01"), TaskOrigin.PROPOSED, List.of()).created(key)));
        api().body(Map.of("status", "READY")).put("/api/sessions/" + id + "/status").then().statusCode(200);
        return id;
    }

    @Test
    void prochainePrendreEtLesRefus() throws Exception {
        String id = feature();
        String secret = AgentResourceIT.secret();
        String base = "/api/agent/features/" + id + "/tasks";
        AgentResourceIT.agent(secret).get(base + "/next").then().statusCode(422).body("code", equalTo("BRIDGE_OUTDATED"));
        AgentResourceIT.agent(secret).header("X-Agent-Connection", ME).get(base + "/next").then().statusCode(200)
                .body("outcome", equalTo("TASK")).body("task.key", equalTo(key)).body("resume", equalTo(false));
        AgentResourceIT.agent(secret).header("X-Agent-Connection", ME).post(base + "/" + key + "/start").then()
                .statusCode(200).body("task.status", equalTo("IN_PROGRESS")).body("jiraSync", equalTo("OK"))
                .body("task.heldByYou", equalTo(true));
        jira.verify(1, postRequestedFor(urlPathEqualTo("/rest/api/2/issue/" + key + "/transitions")));

        // une autre conversation ouverte : le processus de ce test est vivant, sous un autre pid annoncé
        String other = ProcessHandle.current().pid() + "-"
                + ProcessHandle.current().info().startInstant().orElseThrow().toEpochMilli();
        AgentResourceIT.agent(secret).header("X-Agent-Connection", other)
                .post(base + "/" + key + "/start").then().log().ifValidationFails().statusCode(200).body("task.heldByYou", equalTo(true));
        AgentResourceIT.agent(secret).header("X-Agent-Connection", ME).post(base + "/" + key + "/complete")
                .then().statusCode(409).body("code", equalTo("TASK_NOT_READY"));
        AgentResourceIT.agent(secret).header("X-Agent-Connection", other).body(Map.of("report", "x".repeat(2001)))
                .post(base + "/" + key + "/complete").then().statusCode(422).body("code", equalTo("REPORT_TOO_LONG"));
        AgentResourceIT.agent(secret).header("X-Agent-Connection", other).post(base + "/PROJ-1/start").then()
                .statusCode(404);

        AgentResourceIT.agent(secret).header("X-Agent-Connection", other).get("/api/agent/features/" + id).then()
                .statusCode(200).body("tasks", hasSize(1)).body("tasks[0].heldBy", equalTo("Claude Code"))
                .body("progress.total", equalTo(1)).body("tracking", equalTo("ACTIVE")).body("status", equalTo("IN_PROGRESS"));
        AgentResourceIT.agent(secret).body(Map.of("status", "IMPLEMENTED")).put("/api/agent/features/" + id + "/status")
                .then().statusCode(409).body("code", equalTo("STATUS_FOLLOWS_TASKS"));
        api().body(Map.of("status", "IMPLEMENTED")).put("/api/sessions/" + id + "/status").then().statusCode(409)
                .body("code", equalTo("STATUS_FOLLOWS_TASKS"));
        api().body(Map.of("status", "READY")).put("/api/sessions/" + id + "/status").then().statusCode(200);
        AgentResourceIT.agent(secret).header("X-Agent-Connection", other).get(base + "/next").then().statusCode(409)
                .body("code", equalTo("TRACKING_SUSPENDED"));
        api().post("/api/sessions/" + id + "/tasks/resume").then().statusCode(200).body("tracking", equalTo("ACTIVE"));
    }
}
