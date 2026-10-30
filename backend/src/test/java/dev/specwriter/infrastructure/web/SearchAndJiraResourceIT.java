package dev.specwriter.infrastructure.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dev.specwriter.support.LocalApi.api;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T104 : recherche, ticket Jira, modification assistée et envoi, selon contracts/openapi.yaml. LLM rejoué. */
@QuarkusTest
class SearchAndJiraResourceIT {

    static WireMockServer jira;
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
        key = "PAY-" + ThreadLocalRandom.current().nextInt(1, 1_000_000);
        api().body(Map.of("serverUrl", "http://127.0.0.1:" + jira.port(), "defaultProjectKey", "PAY", "acceptInsecureHttp",
                true, "token", "TEST-JIRA-PAT")).put("/api/settings/jira").then().statusCode(200);
        consent("JIRA", true);
        consent("COPILOT", true);
    }

    static void consent(String service, boolean granted) {
        api().body(Map.of("granted", granted)).put("/api/consents/" + service).then().statusCode(204);
    }

    void stubIssue(String description, String updated) {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/" + key)).willReturn(okJson("""
                {"key":"%s","fields":{"summary":"Paiement en ligne","description":"%s","updated":"%s"}}"""
                .formatted(key, description, updated))));
    }

    static final String DESCRIPTION = "h2. Besoin\\nLes clients doivent pouvoir payer leur panier en ligne par carte "
            + "bancaire. IT-IMPORT";

    JsonPath importIssue() {
        stubIssue(DESCRIPTION, "2026-10-30T09:00:00.000+0000");
        return api().post("/api/jira/issues/" + key + "/import").then().statusCode(201).extract().jsonPath();
    }

    @Test
    void detailDUnTicketEnMarkdown() {
        stubIssue(DESCRIPTION, "2026-10-30T09:00:00.000+0000");
        api().get("/api/jira/issues/" + key).then().statusCode(200)
                .body("key", equalTo(key))
                .body("summary", equalTo("Paiement en ligne"))
                .body("descriptionMarkdown", startsWith("## Besoin"))
                .body("updated", equalTo("2026-10-30T09:00:00Z"))
                .body("url", equalTo("http://127.0.0.1:" + jira.port() + "/browse/" + key));
        api().get("/api/jira/issues/pas-une-cle").then().statusCode(422);
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/PAY-0")).willReturn(aResponse().withStatus(404)));
        api().get("/api/jira/issues/PAY-0").then().statusCode(404);
    }

    @Test
    void importPuisRechercheUnifiee() {
        JsonPath imported = importIssue();
        String id = imported.getString("id");
        org.assertj.core.api.Assertions.assertThat(imported.getString("origin")).isEqualTo("JIRA_IMPORT");
        org.assertj.core.api.Assertions.assertThat(imported.getString("jiraKey")).isEqualTo(key);
        org.assertj.core.api.Assertions.assertThat(imported.getString("linkedTicket.syncState")).isEqualTo("IN_SYNC");
        org.assertj.core.api.Assertions.assertThat(imported.getInt("latestVersion")).isEqualTo(1);
        jira.verify(0, putRequestedFor(urlPathEqualTo("/rest/api/2/issue/" + key)));
        api().get("/api/sessions/" + id + "/versions/1").then().statusCode(200).body("origin", equalTo("JIRA_IMPORT"))
                .body("useCases[0].code", equalTo("UC-01"));
        api().post("/api/jira/issues/" + key + "/import").then().statusCode(409).body("code", equalTo("ALREADY_LINKED"));

        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).willReturn(okJson("""
                {"startAt":0,"total":2,"issues":[
                 {"key":"%s","fields":{"summary":"Paiement en ligne","updated":"2026-10-30T09:00:00.000+0000"}},
                 {"key":"PAY-0","fields":{"summary":"Remboursement","updated":"2026-10-29T09:00:00.000+0000"}}]}"""
                .formatted(key))));
        api().queryParam("q", "IT-IMPORT").get("/api/search").then().statusCode(200)
                .body("jiraStatus", equalTo("OK"))
                .body("items.findAll { it.id == '" + id + "' }", hasSize(1))
                .body("items.find { it.id == '" + id + "' }.provenance", equalTo("BOTH"))
                .body("items.find { it.id == '" + id + "' }.jiraKey", equalTo(key))
                .body("items.find { it.id == 'PAY-0' }.provenance", equalTo("JIRA"))
                .body("items.find { it.id == 'PAY-0' }.updatedAt", notNullValue());
        api().get("/api/sessions").then().body("find { it.id == '" + id + "' }.jiraKey", equalTo(key));
    }

    @Test
    void rechercheSansJiraEtSaisieInvalide() {
        consent("JIRA", false);
        api().queryParam("q", "réservation").get("/api/search").then().statusCode(200)
                .body("jiraStatus", equalTo("CONSENT_REQUIRED"));
        api().delete("/api/settings/jira").then().statusCode(204);
        api().queryParam("q", "réservation").get("/api/search").then().statusCode(200)
                .body("jiraStatus", equalTo("NOT_CONFIGURED"));
        api().queryParam("q", "a").get("/api/search").then().statusCode(422);
        api().queryParam("q", "x".repeat(201)).get("/api/search").then().statusCode(422);
        api().get("/api/search").then().statusCode(422);
    }

    @Test
    void jiraEnPanneResultatsLocauxQuandMeme() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/search")).willReturn(aResponse().withStatus(500)));
        api().queryParam("q", "réservation").get("/api/search").then().statusCode(200)
                .body("jiraStatus", equalTo("UNAVAILABLE"));
    }

    @Test
    void modificationAssisteePuisEnvoiEtConflit() {
        String id = importIssue().getString("id");

        // proposition : aucune version tant qu'elle n'est pas acceptée
        JsonPath proposal = api().body(Map.of("instruction", "Ajoute l'annulation IT-EDIT"))
                .post("/api/sessions/" + id + "/ai-edits").then().statusCode(200)
                .body("baseVersion", equalTo(1))
                .body("proposed.title", equalTo("Réservation de salles avec annulation"))
                .body("unifiedDiff", startsWith("--- v1\n+++ proposition"))
                .extract().jsonPath();
        api().get("/api/sessions/" + id + "/versions").then().body("$", hasSize(1));
        api().body(Map.of("instruction", "x".repeat(2001))).post("/api/sessions/" + id + "/ai-edits").then().statusCode(422);
        api().post("/api/sessions/" + id + "/ai-edits/" + java.util.UUID.randomUUID() + "/accept").then().statusCode(404);

        api().post("/api/sessions/" + id + "/ai-edits/" + proposal.getString("proposalId") + "/accept").then().statusCode(201)
                .body("number", equalTo(2)).body("origin", equalTo("AI_EDIT"));
        api().get("/api/sessions/" + id).then().body("linkedTicket.syncState", equalTo("LOCAL_AHEAD"));
        jira.verify(0, putRequestedFor(urlPathEqualTo("/rest/api/2/issue/" + key)));

        // aperçu : la description wiki que Jira recevra, sans appel à Jira
        api().body(Map.of("versionNumber", 2)).post("/api/sessions/" + id + "/jira/preview").then().statusCode(200)
                .body("mode", equalTo("UPDATE")).body("issueKey", equalTo(key))
                .body("descriptionWiki", containsString("h1. Réservation de salles avec annulation"));
        api().body(Map.of()).post("/api/sessions/" + id + "/jira/preview").then().statusCode(422);

        // envoi : confirmation obligatoire, puis conflit car le ticket a changé dans Jira
        api().body(Map.of("versionNumber", 2)).put("/api/sessions/" + id + "/jira").then().statusCode(422);
        api().body(Map.of("confirm", true, "versionNumber", 2, "conflictResolution", "AU_HASARD"))
                .put("/api/sessions/" + id + "/jira").then().statusCode(422);
        stubIssue("Modifié dans Jira IT-MERGE", "2026-10-30T10:00:00.000+0000");
        api().body(Map.of("confirm", true, "versionNumber", 2)).put("/api/sessions/" + id + "/jira").then().statusCode(409)
                .contentType(containsString("application/problem+json"))
                .body("code", equalTo("JIRA_CONFLICT"))
                .body("jiraDescriptionMarkdown", containsString("Modifié dans Jira"))
                .body("localMarkdown", containsString("annulation"))
                .body("jiraUpdated", equalTo("2026-10-30T10:00:00Z"));
        api().get("/api/sessions/" + id).then().body("linkedTicket.syncState", equalTo("CONFLICT"));
        jira.verify(0, putRequestedFor(urlPathEqualTo("/rest/api/2/issue/" + key)));

        // fusion par l'IA : proposition à valider, puis envoi sans conflit
        String merge = api().body(Map.of("confirm", true, "versionNumber", 2, "conflictResolution", "AI_MERGE"))
                .put("/api/sessions/" + id + "/jira").then().statusCode(202)
                .body("proposed.origin", equalTo("CONFLICT_MERGE")).extract().path("proposalId");
        api().post("/api/sessions/" + id + "/ai-edits/" + merge + "/accept").then().statusCode(201)
                .body("number", equalTo(3)).body("origin", equalTo("CONFLICT_MERGE"));

        jira.stubFor(put(urlPathEqualTo("/rest/api/2/issue/" + key)).willReturn(aResponse().withStatus(204)));
        api().body(Map.of("confirm", true, "versionNumber", 3)).put("/api/sessions/" + id + "/jira").then().statusCode(200)
                .body("issueKey", equalTo(key))
                .body("syncState", equalTo("IN_SYNC"))
                .body("linkedVersionNumber", equalTo(3));
        jira.verify(1, putRequestedFor(urlPathEqualTo("/rest/api/2/issue/" + key)));
        api().get("/api/sessions/" + id + "/versions").then()
                .body("find { it.number == 3 }.pushedToJira", equalTo(true))
                .body("pushedToJira", hasItem(false));
    }
}
