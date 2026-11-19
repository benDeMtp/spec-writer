package fr.kawamind.infrastructure.web;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static fr.kawamind.support.LocalApi.api;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.langchain4j.model.chat.ChatModel;
import fr.kawamind.domain.jira.LinkedJiraTicket;
import fr.kawamind.domain.port.LinkedTicketRepository;
import fr.kawamind.support.ScriptedChatModel;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T021, T056 (015, contracts/rest-api.md) : routes du panneau Tâches et leurs codes d'erreur. */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class TaskResourceIT {

    static WireMockServer jira;

    @Inject
    LinkedTicketRepository tickets;

    String key;
    String subtaskKey;

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
        SearchAndJiraResourceIT.consent("COPILOT", true);
        // la base de test survit d'une exécution à l'autre : des clés neuves à chaque fois
        int n = 1000 + ThreadLocalRandom.current().nextInt(1_000_000);
        key = "PROJ-" + n;
        subtaskKey = "PROJ-" + (n + 1);
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/" + key))
                .withQueryParam("fields", equalTo("summary,status,issuetype,subtasks")).willReturn(okJson("""
                        {"key":"%s","fields":{"summary":"Réservation","status":{"name":"Ouvert",
                         "statusCategory":{"key":"new"}},"issuetype":{"name":"Story","subtask":false},"subtasks":[]}}"""
                        .formatted(key))));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/createmeta/PROJ/issuetypes")).willReturn(okJson("""
                {"total":2,"values":[{"id":"10001","name":"Story","subtask":false},
                {"id":"10005","name":"Sous-tâche","subtask":true}]}""")));
        jira.stubFor(post("/rest/api/2/issue").willReturn(okJson("{\"id\":\"3\",\"key\":\"" + subtaskKey + "\"}")));
    }

    void link(String sessionId) {
        Instant t = Instant.parse("2026-11-17T09:00:00Z");
        tickets.save(new LinkedJiraTicket(UUID.fromString(sessionId), key, "http://127.0.0.1:" + jira.port() + "/browse/"
                + key, "Story", 1, t, "h", t, null, LinkedJiraTicket.SyncState.IN_SYNC));
    }

    static String task(String title) {
        return "{\"title\":\"" + title + "\",\"outcome\":\"Résultat " + title
                + "\",\"basisKind\":\"USE_CASE\",\"basisRef\":\"UC-01\",\"dependsOn\":[]}";
    }

    @Test
    void sansTicketLaPropositionEstIndisponible() {
        String id = ExportResourceIT.generated();
        api().get("/api/sessions/" + id + "/tasks").then().statusCode(200)
                .body("canPropose", org.hamcrest.Matchers.equalTo(false))
                .body("proposeBlocked", org.hamcrest.Matchers.equalTo("NO_LINKED_TICKET"))
                .body("tasks", hasSize(0));
        api().post("/api/sessions/" + id + "/tasks/proposal").then().statusCode(409)
                .body("code", org.hamcrest.Matchers.equalTo("NO_LINKED_TICKET"));
    }

    @Test
    void proposerCorrigerValiderPuisNePasSupprimer() {
        String id = ExportResourceIT.generated();
        link(id);
        QuarkusMock.installMockForType(new ScriptedChatModel().then("propose-tasks", List.of("<cas-usage code=\"UC-01\""),
                "{\"tasks\":[" + task("A") + "," + task("B") + "," + task("C") + "]}"), ChatModel.class);
        String base = "/api/sessions/" + id + "/tasks";
        api().get(base).then().statusCode(200).body("canPropose", org.hamcrest.Matchers.equalTo(true))
                .body("linkedIssueKey", org.hamcrest.Matchers.equalTo(key));
        api().post(base + "/proposal").then().statusCode(200)
                .body("basis", org.hamcrest.Matchers.equalTo("USE_CASES"))
                .body("tasks.title", org.hamcrest.Matchers.equalTo(List.of("A", "B", "C")));
        jira.verify(0, postRequestedFor(urlPathEqualTo("/rest/api/2/issue")));

        api().body(Map.of("tasks", List.of(Map.of("title", "A renommée", "outcome", "Résultat A",
                "basis", Map.of("kind", "USE_CASE", "ref", "UC-01"), "dependsOn", List.of(), "manual", false))))
                .put(base + "/proposal").then().statusCode(200)
                .body("tasks.title", org.hamcrest.Matchers.equalTo(List.of("A renommée")));
        api().body(Map.of("tasks", List.of(Map.of("title", "x".repeat(121), "outcome", "R", "dependsOn", List.of(),
                "manual", true)))).put(base + "/proposal").then().statusCode(422);
        api().get(base).then().body("hasProposal", org.hamcrest.Matchers.equalTo(true));

        String taskId = api().post(base + "/proposal/validate").then().statusCode(200)
                .body("tasks", hasSize(1))
                .body("tasks[0].key", org.hamcrest.Matchers.equalTo(subtaskKey))
                .body("tasks[0].url", endsWith("/browse/" + subtaskKey))
                .body("tasks[0].status", org.hamcrest.Matchers.equalTo("TODO"))
                .body("progress.total", org.hamcrest.Matchers.equalTo(1))
                .body("progress.done", org.hamcrest.Matchers.equalTo(0))
                .body("creationProblem", nullValue())
                .body("hasProposal", org.hamcrest.Matchers.equalTo(false))
                .extract().path("tasks[0].id");
        jira.verify(1, postRequestedFor(urlPathEqualTo("/rest/api/2/issue"))
                .withRequestBody(matchingJsonPath("$.fields.parent.key", equalTo(key)))
                .withRequestBody(matchingJsonPath("$.fields.summary", equalTo("A renommée"))));

        api().delete(base + "/" + taskId).then().statusCode(409)
                .body("code", org.hamcrest.Matchers.equalTo("CANNOT_DELETE_CREATED"));
        api().delete(base + "/proposal").then().statusCode(204);
        // la relecture échoue (aucune recherche simulée) : statuts connus et problème signalé
        api().post(base + "/sync").then().statusCode(200)
                .body("readProblem", org.hamcrest.Matchers.equalTo("JIRA_UNREACHABLE"))
                .body("tasks[0].status", org.hamcrest.Matchers.equalTo("TODO"));
        api().post(base + "/" + taskId + "/release").then().statusCode(409)
                .body("code", org.hamcrest.Matchers.equalTo("TASK_NOT_READY"));
    }
}
