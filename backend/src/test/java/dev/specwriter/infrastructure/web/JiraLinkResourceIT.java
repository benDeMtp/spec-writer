package dev.specwriter.infrastructure.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dev.specwriter.support.LocalApi.api;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T127 : aperçu (création et mise à jour) et création du ticket, selon contracts/openapi.yaml. */
@QuarkusTest
class JiraLinkResourceIT {

    static WireMockServer jira;

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
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/createmeta/PROJ/issuetypes")).willReturn(okJson("""
                {"total":2,"values":[{"id":"10002","name":"Bug","subtask":false},{"id":"10001","name":"Story","subtask":false}]}""")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/createmeta/PROJ/issuetypes/10001")).willReturn(okJson("""
                {"total":2,"values":[
                 {"fieldId":"summary","name":"Résumé","required":true,"hasDefaultValue":false,"schema":{"type":"string"}},
                 {"fieldId":"customfield_1","name":"Équipe","required":true,"hasDefaultValue":false,"schema":{"type":"option"},
                  "allowedValues":[{"id":"1","value":"Alpha"}]}]}""")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/createmeta/PROJ/issuetypes/10002"))
                .willReturn(okJson("{\"total\":0,\"values\":[]}")));
    }

    @Test
    void apercuPuisCreationPuisApercuDeMiseAJour() {
        String id = ExportResourceIT.generated();
        // la base de test survit d'une exécution à l'autre : une clé déjà liée serait refusée
        String key = "PROJ-" + (1000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000));
        api().body(Map.of("versionNumber", 1)).post("/api/sessions/" + id + "/jira/preview").then().statusCode(200)
                .body("mode", equalTo("CREATE"))
                .body("projectKey", equalTo("PROJ"))
                .body("issueTypes.name", equalTo(java.util.List.of("Bug", "Story")))
                .body("defaultIssueTypeId", equalTo("10001"))
                .body("summary", equalTo("Réservation de salles"))
                .body("descriptionWiki", startsWith("h1. Réservation de salles"))
                .body("requiredFields", hasSize(1))
                .body("requiredFields[0].name", equalTo("Équipe"))
                .body("requiredFields[0].kind", equalTo("OPTION"))
                .body("requiredFields[0].options[0].label", equalTo("Alpha"));
        api().body(Map.of("versionNumber", 1, "issueTypeId", "10002")).post("/api/sessions/" + id + "/jira/preview")
                .then().statusCode(200).body("issueTypeId", equalTo("10002")).body("requiredFields", hasSize(0));

        // sans confirmation, rien n'est créé
        api().body(Map.of("versionNumber", 1, "issueTypeId", "10001")).post("/api/sessions/" + id + "/jira").then()
                .statusCode(422);
        jira.verify(0, postRequestedFor(urlPathEqualTo("/rest/api/2/issue")));

        jira.stubFor(post("/rest/api/2/issue").willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json").withBody("{\"id\":\"20001\",\"key\":\"" + key + "\",\"self\":\"x\"}")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/" + key)).willReturn(okJson("""
                {"key":"%s","fields":{"summary":"Réservation de salles","description":"h1. Réservation","updated":"2026-10-30T12:00:00.000+0000"}}""".formatted(key))));
        api().body(Map.of("confirm", true, "versionNumber", 1, "issueTypeId", "10001", "extraFields", Map.of("customfield_1", "1")))
                .post("/api/sessions/" + id + "/jira").then().statusCode(201)
                .body("issueKey", equalTo(key))
                .body("issueUrl", equalTo("http://127.0.0.1:" + jira.port() + "/browse/" + key))
                .body("syncState", equalTo("IN_SYNC"))
                .body("linkedVersionNumber", equalTo(1));
        api().get("/api/sessions/" + id).then().body("jiraKey", equalTo(key));
        api().get("/api/sessions/" + id + "/versions").then().body("[0].pushedToJira", equalTo(true));

        api().body(Map.of("confirm", true, "versionNumber", 1, "issueTypeId", "10001")).post("/api/sessions/" + id + "/jira")
                .then().statusCode(409).body("code", equalTo("ALREADY_LINKED"));
        api().body(Map.of("versionNumber", 1)).post("/api/sessions/" + id + "/jira/preview").then().statusCode(200)
                .body("mode", equalTo("UPDATE")).body("issueKey", equalTo(key));
    }

    @Test
    void refusDeJiraEn502AvecSaCause() {
        String id = ExportResourceIT.generated();
        jira.stubFor(post("/rest/api/2/issue").willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                .withBody("{\"errorMessages\":[],\"errors\":{\"components\":\"Composant inconnu\"}}")));
        api().body(Map.of("confirm", true, "versionNumber", 1, "issueTypeId", "10002")).post("/api/sessions/" + id + "/jira")
                .then().statusCode(502).body("code", equalTo("JIRA_REJECTED"))
                .body("userMessage", containsString("Composant inconnu"));
        api().get("/api/sessions/" + id).then().body("jiraKey", equalTo(null));

        jira.stubFor(get(urlPathMatching("/rest/api/2/issue/createmeta/.*")).willReturn(aResponse().withStatus(403)));
        api().body(Map.of("versionNumber", 1)).post("/api/sessions/" + id + "/jira/preview").then().statusCode(403)
                .body("code", equalTo("JIRA_PERMISSION_DENIED"));
    }

    @Test
    void champObligatoireManquant() {
        String id = ExportResourceIT.generated();
        api().body(Map.of("confirm", true, "versionNumber", 1, "issueTypeId", "10001")).post("/api/sessions/" + id + "/jira")
                .then().statusCode(422).body("userMessage", containsString("Équipe"));
        jira.verify(0, postRequestedFor(urlPathEqualTo("/rest/api/2/issue")));
    }
}
