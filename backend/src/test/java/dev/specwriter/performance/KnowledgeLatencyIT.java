package dev.specwriter.performance;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dev.specwriter.support.LocalApi.api;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.specwriter.domain.project.Project;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * T060, SC-004a de 002 : sur 200 tickets indexés, une synchronisation sans changement prend moins
 * de 30 s, et les tickets proches d'un besoin s'affichent en moins de 5 s (LLM rejoué,
 * llm-replay/13 et 15).
 */
@QuarkusTest
class KnowledgeLatencyIT {

    static final int TICKETS = 200;
    static WireMockServer jira;
    String projectId;

    @BeforeAll
    static void start() {
        jira = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        jira.start();
        String issues = IntStream.range(1, TICKETS + 1).mapToObj(i -> """
                {"key":"FACT-%d","fields":{"summary":"%s","description":"Description %d : facture, avoir et relance.",
                 "status":{"name":"Terminé","statusCategory":{"key":"done"}},"issuetype":{"name":"Story"},
                 "updated":"2026-09-12T10:00:00.000+0000"}}""".formatted(i, i == 7 ? "Annuler une facture" : "Ticket " + i, i))
                .collect(Collectors.joining(","));
        String keys = IntStream.range(1, TICKETS + 1).mapToObj(i -> "{\"key\":\"FACT-" + i + "\"}").collect(Collectors.joining(","));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/project/FACT")).willReturn(okJson("{\"key\":\"FACT\"}")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).withQueryParam("jql", matching(".*ORDER BY updated ASC"))
                .withQueryParam("startAt", equalTo("0"))
                .willReturn(okJson("{\"startAt\":0,\"maxResults\":" + TICKETS + ",\"total\":" + TICKETS + ",\"issues\":[" + issues + "]}")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).withQueryParam("jql", matching(".*ORDER BY updated ASC"))
                .withQueryParam("startAt", matching("[1-9][0-9]*"))
                .willReturn(okJson("{\"startAt\":" + TICKETS + ",\"maxResults\":50,\"total\":" + TICKETS + ",\"issues\":[]}")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).withQueryParam("fields", equalTo("key"))
                .willReturn(okJson("{\"startAt\":0,\"maxResults\":" + TICKETS + ",\"total\":" + TICKETS + ",\"issues\":[" + keys + "]}")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/FACT-7")).willReturn(okJson(
                "{\"key\":\"FACT-7\",\"fields\":{\"status\":{\"name\":\"Terminé\",\"statusCategory\":{\"key\":\"done\"}},"
                        + "\"updated\":\"2026-09-12T10:00:00.000+0000\"}}")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/FACT-9")).willReturn(okJson(
                "{\"key\":\"FACT-9\",\"fields\":{\"status\":{\"name\":\"Terminé\",\"statusCategory\":{\"key\":\"done\"}},"
                        + "\"updated\":\"2026-09-12T10:00:00.000+0000\"}}")));
    }

    @AfterAll
    static void stop() {
        api().delete("/api/settings/jira");
        jira.stop();
    }

    @AfterEach
    void cleanUp() {
        api().body(Map.of("projectId", Project.DEFAULT_ID.toString())).put("/api/projects/current").then().statusCode(204);
        api().delete("/api/projects/" + projectId + "?sessions=DELETE").then().statusCode(204);
    }

    long index(String mode) throws InterruptedException {
        long start = System.nanoTime();
        api().body(Map.of("mode", mode)).post("/api/projects/" + projectId + "/indexations").then().statusCode(202);
        long deadline = start + TimeUnit.SECONDS.toNanos(120);
        while (System.nanoTime() < deadline) {
            String status = api().get("/api/projects/" + projectId + "/indexations/current").then().extract().path("status");
            if (!"RUNNING".equals(status)) {
                assertThat(status).isEqualTo("SUCCEEDED");
                return (System.nanoTime() - start) / 1_000_000;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("indexation trop longue");
    }

    @Test
    void synchronisationSansChangementEtTicketsProches() throws Exception {
        api().body(Map.of("serverUrl", "http://127.0.0.1:" + jira.port(), "defaultProjectKey", "PROJ", "acceptInsecureHttp",
                true, "token", "TEST-JIRA-PAT")).put("/api/settings/jira").then().statusCode(200);
        api().body(Map.of("granted", true)).put("/api/consents/JIRA").then().statusCode(204);
        api().body(Map.of("granted", true)).put("/api/consents/COPILOT").then().statusCode(204);
        projectId = api().body(Map.of("name", "Latence " + UUID.randomUUID(), "jiraProjectKey", "FACT"))
                .post("/api/projects").then().statusCode(201).extract().path("id");
        api().body(Map.of("projectId", projectId)).put("/api/projects/current").then().statusCode(204);
        index("FULL");
        assertThat(api().get("/api/projects/" + projectId + "/knowledge").then().extract().jsonPath().getInt("tickets"))
                .isEqualTo(TICKETS);

        long sync = index("INCREMENTAL");
        assertThat(sync).as("synchronisation sans changement, en ms").isLessThan(30_000);

        long start = System.nanoTime();
        JsonPath created = api().body(Map.of("need",
                "IT-PROCHES Le gestionnaire doit pouvoir annuler une facture émise par un avoir total."))
                .post("/api/sessions").then().statusCode(201).extract().jsonPath();
        api().get("/api/sessions/" + created.getString("id") + "/related-issues").then().statusCode(200);
        long related = (System.nanoTime() - start) / 1_000_000;
        assertThat(related).as("tickets proches, en ms").isLessThan(5_000);
    }
}
