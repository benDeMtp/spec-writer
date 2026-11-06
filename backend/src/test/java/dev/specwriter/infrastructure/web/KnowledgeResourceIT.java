package dev.specwriter.infrastructure.web;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dev.specwriter.support.LocalApi.api;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T105 : {@code /architecture-source}, {@code /indexations}, {@code /knowledge} de bout en bout
 * sur le jeu fictif (BTR/knowledge), avec Jira simulé et le modèle d'embedding de test.
 */
@QuarkusTest
class KnowledgeResourceIT {

    static WireMockServer jira;
    static Path knowledge;

    String projectId;

    @BeforeAll
    static void start() throws URISyntaxException, IOException {
        jira = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        jira.start();
        knowledge = Path.of(KnowledgeResourceIT.class.getResource("/knowledge").toURI());
    }

    @AfterAll
    static void stop() {
        jira.stop();
    }

    /** Réponse de recherche Jira construite à partir de {@code knowledge/jira/issues.json}. */
    @SuppressWarnings("unchecked")
    static String issues(boolean keysOnly) throws IOException {
        ObjectMapper json = new ObjectMapper();
        List<Map<String, Object>> fixture = json.readValue(knowledge.resolve("jira/issues.json").toFile(), List.class);
        List<Map<String, Object>> issues = new ArrayList<>();
        for (Map<String, Object> i : fixture) {
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("key", i.get("key"));
            if (!keysOnly) {
                issue.put("fields", Map.of("summary", i.get("summary"), "description", i.get("description"),
                        "status", Map.of("name", i.get("status"), "statusCategory", Map.of("key", i.get("statusCategory"))),
                        "issuetype", Map.of("name", i.get("issueType")), "updated", i.get("updated")));
            }
            issues.add(issue);
        }
        return json.writeValueAsString(Map.of("startAt", 0, "maxResults", 50, "total", issues.size(), "issues", issues));
    }

    @BeforeEach
    void setUp() throws IOException {
        jira.resetAll();
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/project/FACT")).willReturn(okJson("{\"key\":\"FACT\"}")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).withQueryParam("jql", matching(".*ORDER BY updated ASC"))
                .willReturn(okJson(issues(false))));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).withQueryParam("fields", com.github.tomakehurst.wiremock.client.WireMock.equalTo("key"))
                .willReturn(okJson(issues(true))));
        api().body(Map.of("serverUrl", "http://127.0.0.1:" + jira.port(), "defaultProjectKey", "PROJ", "acceptInsecureHttp",
                true, "token", "TEST-JIRA-PAT")).put("/api/settings/jira").then().statusCode(200);
        SearchAndJiraResourceIT.consent("JIRA", true);
        projectId = api().body(Map.of("name", "Connaissance " + UUID.randomUUID(), "jiraProjectKey", "FACT"))
                .post("/api/projects").then().statusCode(201).extract().path("id");
    }

    @AfterEach
    void deleteProject() {
        api().delete("/api/projects/" + projectId + "/indexations/current").then().statusCode(204);
        api().delete("/api/projects/" + projectId + "?sessions=DELETE").then().statusCode(204);
    }

    String base() {
        return "/api/projects/" + projectId;
    }

    JsonPath index(String mode) {
        api().body(Map.of("mode", mode)).post(base() + "/indexations").then().statusCode(202)
                .body("status", equalTo("RUNNING")).body("mode", equalTo(mode));
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
        while (true) {
            JsonPath state = api().get(base() + "/indexations/current").then().statusCode(200).extract().jsonPath();
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

    /** 004, US2 : ADR du projet, et leur page servie depuis le dossier déclaré, sans script. */
    @Test
    void adrDuProjetEtLeurPage() {
        api().body(Map.of("kind", "FOLDER", "location", knowledge.resolve("antora").toString()))
                .post(base() + "/doc-sources").then().statusCode(201);
        assertThat(index("FULL").getString("status")).isEqualTo("SUCCEEDED");

        JsonPath adrs = api().get(base() + "/knowledge/adrs").then().statusCode(200).extract().jsonPath();
        assertThat(adrs.getList("adrs.number")).containsExactly(2, 4, 5);
        assertThat(adrs.getString("adrs[0].status")).isEqualTo("SUPERSEDED");
        assertThat(adrs.getString("adrs[0].supersededBy")).isEqualTo("ADR-005");
        assertThat(adrs.getBoolean("adrs[0].successorFound")).isTrue();
        assertThat(adrs.getBoolean("adrs[0].sentToAi")).isFalse();
        assertThat(adrs.getBoolean("adrs[0].duplicate")).isFalse();
        assertThat(adrs.getInt("byStatus.ACCEPTED")).isEqualTo(2);
        assertThat(adrs.getBoolean("indexing")).isFalse();
        String url = adrs.getString("adrs[2].url");
        assertThat(url).isEqualTo(base() + "/doc-files/facturation/adr/adr-005.html");

        io.restassured.response.Response page = api().get(url);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.contentType()).startsWith("text/html");
        assertThat(page.header("Content-Security-Policy")).isEqualTo(
                "sandbox allow-same-origin; script-src 'none'; object-src 'none'; default-src 'self' 'unsafe-inline'");
        assertThat(page.asString()).contains("ADR-005");

        api().get("/api/projects/" + UUID.randomUUID() + "/knowledge/adrs").then().statusCode(404);
    }

    @Test
    void indexationCompletePuisRecherche() {
        api().body(Map.of("kind", "FOLDER", "location", knowledge.resolve("antora").toString()))
                .post(base() + "/doc-sources").then().statusCode(201);
        String workspace = knowledge.resolve("c4/facturation/workspace.dsl").toString();
        api().body(Map.of("workspace", workspace, "ownership", "facturation")).put(base() + "/architecture-source")
                .then().statusCode(200).body("architectureWorkspace", equalTo(workspace))
                .body("architectureOwnership", equalTo("facturation"));
        api().get(base() + "/indexations/current").then().statusCode(404);

        JsonPath state = index("FULL");
        assertThat(state.getString("status")).isEqualTo("SUCCEEDED");
        assertThat(state.getList("errors")).isEmpty();
        assertThat(state.getString("finishedAt")).isNotNull();
        // 003, FR-009 : premier passage sans repère, tout est relu
        assertThat(state.getMap("tickets")).containsEntry("read", 11).containsEntry("keptClosed", 0)
                .containsEntry("removed", 0);

        api().get(base() + "/knowledge").then().statusCode(200)
                .body("tickets", equalTo(11)).body("adrs", equalTo(3)).body("architectureElements", equalTo(10))
                .body("model", equalTo("READY")).body("partial", equalTo(false)).body("syncedAt", notNullValue());
        JsonPath status = api().get(base() + "/knowledge").then().extract().jsonPath();
        assertThat(status.getInt("docSections")).isEqualTo(7);

        JsonPath tickets = api().queryParam("q", "annuler une facture").queryParam("kinds", "TICKET")
                .get(base() + "/knowledge/search").then().statusCode(200).extract().jsonPath();
        assertThat(tickets.getString("[0].ref")).isEqualTo("FACT-7");
        assertThat(tickets.getString("[0].url")).endsWith("/browse/FACT-7");
        assertThat(tickets.getList("kind")).containsOnly("TICKET");

        JsonPath adrs = api().queryParam("q", "notification asynchrone de la comptabilité").queryParam("kinds", "ADR")
                .get(base() + "/knowledge/search").then().statusCode(200).extract().jsonPath();
        assertThat(adrs.getList("ref")).contains("facturation/adr/adr-005.html");
        assertThat(adrs.getList("status")).contains("ACCEPTED", "SUPERSEDED");

        JsonPath modules = api().queryParam("q", "avoirs").queryParam("kinds", "ARCH_ELEMENT")
                .get(base() + "/knowledge/search").then().statusCode(200).extract().jsonPath();
        assertThat(modules.getList("ref")).contains("Facturation/Service Avoirs");
        assertThat(modules.getList("scope")).contains("PROJECT");

        String project = "find { it.id == '" + projectId + "' }";
        api().get("/api/projects").then().statusCode(200).body(project + ".knowledge.tickets", equalTo(11))
                .body(project + ".architectureWorkspace", equalTo(workspace));

        // une synchronisation incrémentale ne change rien
        assertThat(index("INCREMENTAL").getString("status")).isEqualTo("SUCCEEDED");
        api().get(base() + "/knowledge").then().body("tickets", equalTo(11)).body("architectureElements", equalTo(10));

        api().delete(base() + "/architecture-source").then().statusCode(204);
        api().get(base() + "/knowledge").then().body("architectureElements", equalTo(0));
    }

    @Test
    void workspaceEnErreurSignaleFichierEtLigne() {
        api().body(Map.of("workspace", knowledge.resolve("c4/broken/workspace.dsl").toString()))
                .put(base() + "/architecture-source").then().statusCode(200);
        JsonPath state = index("FULL");
        assertThat(state.getString("status")).isEqualTo("SUCCEEDED");
        assertThat(state.getList("errors.kind")).containsExactly("ARCHITECTURE");
        assertThat(state.getString("errors[0].location")).endsWith("workspace.dsl:4");
        api().get(base() + "/knowledge").then().body("partial", equalTo(true));
    }

    /** 005, US2 : sources proposées au choix, puis réindexation de la seule documentation. */
    @Test
    void reindexationDeSourcesChoisies() {
        String docId = api().body(Map.of("kind", "FOLDER", "location", knowledge.resolve("antora").toString()))
                .post(base() + "/doc-sources").then().statusCode(201).extract().path("id");
        JsonPath status = api().get(base() + "/knowledge").then().statusCode(200).extract().jsonPath();
        assertThat(status.getList("sources.id")).containsExactly("JIRA", docId);
        assertThat(status.getList("sources.label")).containsExactly("Jira FACT", knowledge.resolve("antora").toString());
        assertThat(status.getList("sources.kind")).containsExactly("JIRA", "DOCUMENTATION");

        api().body(Map.of("mode", "FULL", "sources", List.of())).post(base() + "/indexations").then().statusCode(422);
        api().body(Map.of("mode", "FULL", "sources", List.of("WIKI"))).post(base() + "/indexations").then()
                .statusCode(422);
        api().body(Map.of("mode", "INCREMENTAL", "sources", List.of(docId))).post(base() + "/indexations").then()
                .statusCode(422);

        jira.resetRequests();
        api().body(Map.of("mode", "FULL", "sources", List.of(docId))).post(base() + "/indexations").then()
                .statusCode(202).body("sources", contains(docId));
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
        JsonPath state;
        do {
            state = api().get(base() + "/indexations/current").then().statusCode(200).extract().jsonPath();
        } while ("RUNNING".equals(state.getString("status")) && System.nanoTime() < deadline);
        assertThat(state.getString("status")).isEqualTo("SUCCEEDED");
        assertThat(state.getList("sources")).containsExactly(docId);
        assertThat(state.getMap("tickets")).isNull();
        jira.verify(0, com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathEqualTo("/rest/api/2/search")));
        api().get(base() + "/knowledge").then().statusCode(200).body("tickets", equalTo(0)).body("adrs", equalTo(3))
                .body("syncedAt", nullValue());
    }

    @Test
    void entreesRefuseesAvecUnMotif() {
        api().body(Map.of("workspace", "relatif/workspace.dsl")).put(base() + "/architecture-source").then()
                .statusCode(422).body("userMessage", containsString("chemin absolu"));
        api().body(Map.of("workspace", knowledge.resolve("c4").toString())).put(base() + "/architecture-source").then()
                .statusCode(422).body("userMessage", containsString("dossier"));
        api().body(Map.of("mode", "TOUT")).post(base() + "/indexations").then().statusCode(422);
        api().queryParam("q", " ").get(base() + "/knowledge/search").then().statusCode(422);
        api().queryParam("q", "x".repeat(2_001)).get(base() + "/knowledge/search").then().statusCode(422);
        api().queryParam("q", "avoir").queryParam("kinds", "PAGE").get(base() + "/knowledge/search").then()
                .statusCode(422);
        api().get("/api/projects/" + UUID.randomUUID() + "/knowledge").then().statusCode(404);
        api().get(base() + "/indexations/current").then().statusCode(404);
    }
}
