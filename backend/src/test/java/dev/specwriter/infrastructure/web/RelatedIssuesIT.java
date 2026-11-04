package dev.specwriter.infrastructure.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dev.specwriter.support.LocalApi.api;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.specwriter.domain.project.Project;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T108 : tickets proches avant les questions, comparaison, choix, puis story créée et liée par
 * « relates to » (FR-034, FR-035), avec Jira simulé et le LLM rejoué (llm-replay/13 à 16).
 */
@QuarkusTest
class RelatedIssuesIT {

    static final String NEED = "IT-PROCHES Le gestionnaire doit pouvoir annuler une facture émise par un avoir total.";

    static WireMockServer jira;

    String projectId;

    @BeforeAll
    static void start() {
        jira = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        jira.start();
    }

    @AfterAll
    static void stop() {
        jira.stop();
    }

    static String issue(String key, String summary, String status, String category, String updated) {
        return """
                {"key":"%s","fields":{"summary":"%s","description":"Description de %s : facture et avoir.",
                 "status":{"name":"%s","statusCategory":{"key":"%s"}},"issuetype":{"name":"Story"},"updated":"%s"}}"""
                .formatted(key, summary, summary, status, category, updated);
    }

    @BeforeEach
    void setUp() {
        jira.resetAll();
        String fact7 = issue("FACT-7", "Annuler une facture", "Terminé", "done", "2026-09-12T10:00:00.000+0000");
        String fact9 = issue("FACT-9", "Émettre un avoir partiel", "À faire", "new", "2026-10-28T08:30:00.000+0000");
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/project/FACT")).willReturn(okJson("{\"key\":\"FACT\"}")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).withQueryParam("jql", matching(".*ORDER BY updated ASC"))
                .willReturn(okJson("{\"startAt\":0,\"maxResults\":50,\"total\":2,\"issues\":[" + fact7 + "," + fact9 + "]}")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search"))
                .withQueryParam("fields", com.github.tomakehurst.wiremock.client.WireMock.equalTo("key"))
                .willReturn(okJson("{\"startAt\":0,\"maxResults\":100,\"total\":2,\"issues\":[{\"key\":\"FACT-7\"},"
                        + "{\"key\":\"FACT-9\"}]}")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/FACT-7")).willReturn(okJson(fact7)));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/FACT-9")).willReturn(okJson(fact9)));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/createmeta/FACT/issuetypes")).willReturn(okJson("""
                {"total":1,"values":[{"id":"10001","name":"Story","subtask":false}]}""")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/createmeta/FACT/issuetypes/10001"))
                .willReturn(okJson("{\"total\":0,\"values\":[]}")));
        jira.stubFor(post("/rest/api/2/issue").willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json").withBody("{\"id\":\"20020\",\"key\":\"FACT-20\"}")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/FACT-20")).willReturn(okJson(
                issue("FACT-20", "Annulation", "À faire", "new", "2026-11-04T10:00:00.000+0000"))));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issueLinkType")).willReturn(okJson("""
                {"issueLinkTypes":[{"id":"10003","name":"Relates","inward":"relates to","outward":"relates to"}]}""")));
        jira.stubFor(post("/rest/api/2/issueLink").willReturn(aResponse().withStatus(201)));

        api().body(Map.of("serverUrl", "http://127.0.0.1:" + jira.port(), "defaultProjectKey", "PROJ", "acceptInsecureHttp",
                true, "token", "TEST-JIRA-PAT")).put("/api/settings/jira").then().statusCode(200);
        SearchAndJiraResourceIT.consent("JIRA", true);
        SearchAndJiraResourceIT.consent("COPILOT", true);
        projectId = api().body(Map.of("name", "Proches " + UUID.randomUUID(), "jiraProjectKey", "FACT"))
                .post("/api/projects").then().statusCode(201).extract().path("id");
        api().body(Map.of("projectId", projectId)).put("/api/projects/current").then().statusCode(204);
        index();
    }

    /** Le projet par défaut redevient courant : les autres tests n'ont pas de tickets indexés. */
    @AfterEach
    void cleanUp() {
        api().body(Map.of("projectId", Project.DEFAULT_ID.toString())).put("/api/projects/current").then().statusCode(204);
        api().delete("/api/projects/" + projectId + "?sessions=DELETE").then().statusCode(204);
    }

    void index() {
        api().body(Map.of("mode", "FULL")).post("/api/projects/" + projectId + "/indexations").then().statusCode(202);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            String status = api().get("/api/projects/" + projectId + "/indexations/current").then().extract()
                    .path("status");
            if (!"RUNNING".equals(status)) {
                assertThat(status).isEqualTo("SUCCEEDED");
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Test
    void ticketsProchesPuisStoryLieeAuTicketFerme() {
        JsonPath created = api().body(Map.of("need", NEED)).post("/api/sessions").then().statusCode(201).extract()
                .jsonPath();
        String id = created.getString("id");
        // les questions attendent le choix
        assertThat(created.getList("currentBatch")).isEmpty();

        JsonPath related = api().get("/api/sessions/" + id + "/related-issues").then().statusCode(200).extract()
                .jsonPath();
        assertThat(related.getString("status")).isEqualTo("READY");
        assertThat(related.getList("issues.key")).containsExactly("FACT-7", "FACT-9");
        assertThat(related.getBoolean("issues[0].closed")).isTrue();
        assertThat(related.getBoolean("issues[0].statusFresh")).isTrue();
        assertThat(related.getString("issues[0].reason")).contains("avoir total");
        assertThat(related.getString("issues[0].url")).endsWith("/browse/FACT-7");
        assertThat(related.getString("decision")).isNull();

        api().post("/api/sessions/" + id + "/related-issues/FACT-9/comparison").then().statusCode(200)
                .body("suggestion", equalTo("DIFFERENT")).body("common", hasSize(1)).body("different", hasSize(1));

        api().body(Map.of("decision", "LINK_NEW", "key", "FACT-7")).put("/api/sessions/" + id + "/related-issues/decision")
                .then().statusCode(200).body("sessionId", equalTo(id)).body("relatesTo", equalTo("FACT-7"));
        api().get("/api/sessions/" + id + "/related-issues").then().body("decision", equalTo("LINK_NEW"));

        // la story cite le ticket proche, ramené à sa clé exacte, avec son lien (T110, T116)
        api().post("/api/sessions/" + id + "/versions").then().statusCode(201)
                .body("useCases[0].source.kind", equalTo("JIRA_ISSUE")).body("useCases[0].source.ref", equalTo("FACT-7"))
                .body("links['FACT-7']", equalTo("http://127.0.0.1:" + jira.port() + "/browse/FACT-7"))
                .body("architecture.modules", hasSize(0)).body("adrDeviations", hasSize(0));
        api().body(Map.of("versionNumber", 1)).post("/api/sessions/" + id + "/jira/preview").then().statusCode(200)
                .body("relatesTo", equalTo("FACT-7")).body("projectKey", equalTo("FACT"));
        api().body(Map.of("confirm", true, "versionNumber", 1, "issueTypeId", "10001")).post("/api/sessions/" + id + "/jira")
                .then().statusCode(201).body("issueKey", equalTo("FACT-20")).body("relatesTo", equalTo("FACT-7"))
                .body("relatesToLink", equalTo("LINKED"));
        jira.verify(postRequestedFor(urlPathEqualTo("/rest/api/2/issueLink")).withRequestBody(equalToJson("""
                {"type":{"name":"Relates"},"outwardIssue":{"key":"FACT-20"},"inwardIssue":{"key":"FACT-7"}}""")));
    }

    @Test
    void lienEnEchecPuisReessaye() {
        jira.stubFor(post("/rest/api/2/issueLink").willReturn(aResponse().withStatus(403)));
        String id = api().body(Map.of("need", NEED)).post("/api/sessions").then().statusCode(201).extract().path("id");
        api().body(Map.of("decision", "LINK_NEW", "key", "FACT-7")).put("/api/sessions/" + id + "/related-issues/decision")
                .then().statusCode(200);
        api().post("/api/sessions/" + id + "/versions").then().statusCode(201);
        api().body(Map.of("confirm", true, "versionNumber", 1, "issueTypeId", "10001")).post("/api/sessions/" + id + "/jira")
                .then().statusCode(201).body("issueKey", equalTo("FACT-20")).body("relatesToLink", equalTo("FAILED"));

        jira.stubFor(post("/rest/api/2/issueLink").willReturn(aResponse().withStatus(201)));
        api().post("/api/sessions/" + id + "/jira/relation/retry").then().statusCode(200)
                .body("relatesToLink", equalTo("LINKED")).body("issueKey", equalTo("FACT-20"));
    }

    @Test
    void choixRefuses() {
        String id = api().body(Map.of("need", NEED)).post("/api/sessions").then().statusCode(201).extract().path("id");
        api().body(Map.of("decision", "LINK_NEW", "key", "FACT-9")).put("/api/sessions/" + id + "/related-issues/decision")
                .then().statusCode(422);
        api().body(Map.of("decision", "PEUT-ETRE")).put("/api/sessions/" + id + "/related-issues/decision").then()
                .statusCode(422);
        api().post("/api/sessions/" + id + "/related-issues/AUTRE-1/comparison").then().statusCode(404);
        api().get("/api/sessions/" + UUID.randomUUID() + "/related-issues").then().statusCode(404);
    }
}
