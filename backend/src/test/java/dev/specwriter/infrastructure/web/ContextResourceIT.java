package dev.specwriter.infrastructure.web;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dev.specwriter.support.LocalApi.api;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T025 : {@code /api/projects/{id}/context*}, {@code /doc-sources*} et {@code /context-proposals*}
 * (contracts/openapi.yaml de 002, US2), avec le site Antora de test, Jira simulé et le LLM rejoué.
 */
@QuarkusTest
class ContextResourceIT {

    static WireMockServer jira;
    static String folder;

    String projectId;

    @BeforeAll
    static void start() throws URISyntaxException {
        jira = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        jira.start();
        folder = Path.of(ContextResourceIT.class.getResource("/antora-site").toURI()).toString();
    }

    @AfterAll
    static void stop() {
        jira.stop();
    }

    @BeforeEach
    void setUp() {
        jira.resetAll();
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/project/FACT")).willReturn(okJson("{\"key\":\"FACT\"}")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).willReturn(okJson("""
                {"startAt":0,"maxResults":50,"total":1,"issues":[{"key":"FACT-1","fields":{"summary":"Avoirs",
                 "description":"Le gestionnaire valide les avoirs.","updated":"2026-10-01T08:00:00.000+0000"}}]}""")));
        api().body(Map.of("serverUrl", "http://127.0.0.1:" + jira.port(), "defaultProjectKey", "PROJ", "acceptInsecureHttp",
                true, "token", "TEST-JIRA-PAT")).put("/api/settings/jira").then().statusCode(200);
        SearchAndJiraResourceIT.consent("JIRA", true);
        SearchAndJiraResourceIT.consent("COPILOT", true);
        projectId = api().body(Map.of("name", "Contexte " + UUID.randomUUID(), "jiraProjectKey", "FACT"))
                .post("/api/projects").then().statusCode(201).extract().path("id");
    }

    /** La base survit d'un test à l'autre : un projet laissé fausserait le projet courant de repli. */
    @AfterEach
    void deleteProject() {
        api().delete("/api/projects/" + projectId + "?sessions=DELETE").then().statusCode(204);
    }

    String addFolder() {
        return api().body(Map.of("kind", "FOLDER", "location", folder)).post("/api/projects/" + projectId + "/doc-sources")
                .then().statusCode(201).body("kind", equalTo("FOLDER")).body("location", equalTo(folder))
                .extract().path("id");
    }

    /** Démarre la génération, puis l'interroge jusqu'à sa fin (research.md R14). */
    JsonPath generate() {
        api().post("/api/projects/" + projectId + "/context-generations").then().statusCode(202)
                .body("status", equalTo("RUNNING")).body("startedAt", notNullValue());
        return poll();
    }

    JsonPath poll() {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (true) {
            JsonPath state = api().get("/api/projects/" + projectId + "/context-generations/current").then().statusCode(200)
                    .extract().jsonPath();
            if (!"RUNNING".equals(state.getString("status")) || System.nanoTime() > deadline) {
                return state;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return state;
            }
        }
    }

    @Test
    void apercuPropositionPuisAcceptation() {
        addFolder();
        api().get("/api/projects/" + projectId + "/context-proposals/preview").then().statusCode(200)
                .body("pages", equalTo(31)).body("tickets", equalTo(1)).body("unreadable", hasSize(0));

        api().get("/api/projects/" + projectId + "/context-generations/current").then().statusCode(404);
        JsonPath state = generate();
        assertThat(state.getString("status")).isEqualTo("SUCCEEDED");
        assertThat(state.getString("step")).isEqualTo("BUILDING");
        assertThat(state.getString("proposal.functional.markdown")).contains("[source : billing/regle-tva.html]");
        assertThat(state.getString("proposal.technical.markdown")).startsWith("## Architecture");
        assertThat(state.getList("proposal.questions.sheet")).containsExactly("TECHNICAL", "FUNCTIONAL");
        assertThat(state.getList("proposal.questions.state")).containsOnly("OPEN");
        assertThat(state.getList("proposal.sources")).containsExactly("FACT-1", "billing/regle-tva.html");
        assertThat(state.getList("proposal.unread.location")).contains("billing/recherche.html");
        assertThat(state.getString("proposal.functional.unifiedDiff")).isNotNull();
        assertThat(state.getBoolean("proposal.technical.changed")).isTrue();
        assertThat(state.getList("errors.location")).contains("billing/recherche.html");
        assertThat(state.getList("errors.reason")).contains("NOT_ANTORA");
        String proposalId = state.getString("proposal.proposalId");

        // rien n'est enregistré avant l'acceptation
        api().get("/api/projects/current").then().statusCode(200);
        accept(proposalId, "FUNCTIONAL").statusCode(200)
                .body("functionalContext.origin", equalTo("GENERATED"))
                .body("functionalContext.markdown", containsString("## Règles métier"))
                .body("functionalContext.updatedAt", notNullValue())
                .body("technicalContext.origin", equalTo("NONE"))
                .body("docSources[0].pagesRead", equalTo(30))
                .body("docSources[0].pagesInError", equalTo(0))
                .body("docSources[0].lastReadAt", notNullValue());
        accept(proposalId, "TECHNICAL").statusCode(200).body("technicalContext.origin", equalTo("GENERATED"));
        accept(proposalId, "TECHNICAL").statusCode(404);
        accept(proposalId).statusCode(422);
    }

    io.restassured.response.ValidatableResponse accept(String proposalId, String... sheets) {
        return api().body(Map.of("sheets", java.util.List.of(sheets)))
                .post("/api/projects/" + projectId + "/context-proposals/" + proposalId + "/accept").then();
    }

    @Test
    void questionsRepondueEtPasseePuisRevisionCitantLaReponse() {
        addFolder();
        JsonPath state = generate();
        String proposalId = state.getString("proposal.proposalId");
        String technicalQuestion = state.getString("proposal.questions[0].id");
        String functionalQuestion = state.getString("proposal.questions[1].id");
        String base = "/api/projects/" + projectId + "/context-proposals/" + proposalId + "/questions/";
        api().body(Map.of("answer", "L'ERP")).put(base + technicalQuestion).then().statusCode(200)
                .body("number", equalTo(1)).body("state", equalTo("ANSWERED")).body("sheet", equalTo("TECHNICAL"));
        api().body(Map.of("skip", true)).put(base + functionalQuestion).then().statusCode(200)
                .body("number", equalTo(2)).body("state", equalTo("SKIPPED"));
        api().body(Map.of("answer", " ")).put(base + functionalQuestion).then().statusCode(422);
        JsonPath current = api().get("/api/projects/" + projectId + "/context-generations/current").then().statusCode(200)
                .extract().jsonPath();
        assertThat(current.getList("proposal.questions.state")).containsExactly("ANSWERED", "SKIPPED");
        assertThat(current.getList("proposal.questions.number")).containsExactly(1, 2);

        api().get("/api/projects/" + projectId + "/context-questions").then().statusCode(200)
                .body("number", equalTo(java.util.List.of(1, 2))).body("[0].answer", equalTo("L'ERP"));

        api().post("/api/projects/" + projectId + "/context-proposals/" + proposalId + "/revise").then().statusCode(202)
                .body("status", equalTo("RUNNING"));
        JsonPath revised = poll();
        assertThat(revised.getString("status")).isEqualTo("SUCCEEDED");
        assertThat(revised.getString("proposal.technical.markdown")).contains("[source : Q-1]");
        assertThat(revised.getList("proposal.sources")).contains("Q-1");
        assertThat(revised.getList("proposal.questions")).isEmpty();
        api().post("/api/projects/" + projectId + "/context-proposals/" + proposalId + "/revise").then().statusCode(404);

        // une réponse modifiée ne change aucune fiche
        api().body(Map.of("answer", "Simplifier la facturation")).put("/api/projects/" + projectId + "/context-questions/2")
                .then().statusCode(200).body("state", equalTo("ANSWERED"));
        api().get("/api/projects/current").then().statusCode(200);
    }

    @Test
    void propositionRefuseeSiLeContexteAChange() {
        addFolder();
        String proposalId = generate().getString("proposal.proposalId");
        api().body(Map.of("markdown", "## Acteurs\n- modifié à la main")).put("/api/projects/" + projectId
                + "/context/functional").then().statusCode(200).body("functionalContext.origin", equalTo("EDITED"))
                .body("functionalContext.markdown", equalTo("## Acteurs\n- modifié à la main"));
        accept(proposalId, "FUNCTIONAL").statusCode(409).body("code", equalTo("CONTEXT_CHANGED"));
        // la fiche technique, inchangée, reste acceptable
        accept(proposalId, "TECHNICAL").statusCode(200).body("technicalContext.origin", equalTo("GENERATED"));
    }

    @Test
    void sourcesAjouteesSupprimeesEtValidees() {
        String id = addFolder();
        api().get("/api/projects").then().statusCode(200);
        api().body(Map.of("kind", "URL", "location", "https://moi:secret@docs.example.com/")).post("/api/projects/"
                + projectId + "/doc-sources").then().statusCode(422);
        api().body(Map.of("kind", "FTP", "location", "x")).post("/api/projects/" + projectId + "/doc-sources").then()
                .statusCode(422);
        api().delete("/api/projects/" + projectId + "/doc-sources/" + id).then().statusCode(204);
        api().delete("/api/projects/" + projectId + "/doc-sources/" + id).then().statusCode(404);
        api().body(Map.of("markdown", "x".repeat(20_001))).put("/api/projects/" + projectId + "/context/technical").then()
                .statusCode(422);
        api().body(Map.of("markdown", "x")).put("/api/projects/" + projectId + "/context/autre").then().statusCode(404);
        api().get("/api/projects/" + UUID.randomUUID() + "/context-proposals/preview").then().statusCode(404)
                .body("code", equalTo("PROJECT_NOT_FOUND"));
    }

    @Test
    void sansSourceNiTicketLaGenerationEstRefusee() {
        SearchAndJiraResourceIT.consent("JIRA", false);
        JsonPath state = generate();
        assertThat(state.getString("status")).isEqualTo("FAILED");
        assertThat(state.getString("errors[-1].kind")).isEqualTo("FAILURE");
        assertThat(state.getString("errors[-1].reason")).isEqualTo("VALIDATION_FAILED");
        // sans consentement Copilot, rien ne démarre
        SearchAndJiraResourceIT.consent("COPILOT", false);
        api().post("/api/projects/" + projectId + "/context-generations").then().statusCode(428);
        SearchAndJiraResourceIT.consent("COPILOT", true);
    }
}
