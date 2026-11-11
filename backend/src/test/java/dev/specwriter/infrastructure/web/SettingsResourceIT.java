package dev.specwriter.infrastructure.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dev.specwriter.support.LocalApi.api;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contrat de {@code /api/settings/**} et {@code /api/consents/{service}} (contracts/openapi.yaml). */
@QuarkusTest
class SettingsResourceIT {

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
    void reset() {
        jira.resetAll();
        api().delete("/api/settings/jira").then().statusCode(204);
        consent("JIRA", true);
    }

    private static void consent(String service, boolean granted) {
        api().body(Map.of("granted", granted)).put("/api/consents/" + service).then().statusCode(204);
    }

    private static Map<String, Object> jiraBody(String token) {
        var body = new java.util.HashMap<String, Object>(Map.of("serverUrl", "http://127.0.0.1:" + jira.port(),
                "defaultProjectKey", "PROJ", "acceptInsecureHttp", true));
        if (token != null) {
            body.put("token", token);
        }
        return body;
    }

    private static void stubHealthyJira(String version, boolean canEdit) {
        jira.stubFor(get("/rest/api/2/serverInfo").willReturn(okJson("{\"version\":\"" + version + "\"}")));
        jira.stubFor(get("/rest/api/2/myself").willReturn(okJson("{\"name\":\"ada\"}")));
        jira.stubFor(get("/rest/api/2/project/PROJ").willReturn(okJson("{\"key\":\"PROJ\"}")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/mypermissions")).willReturn(okJson("""
                {"permissions":{"BROWSE_PROJECTS":{"havePermission":true},"CREATE_ISSUES":{"havePermission":true},
                "EDIT_ISSUES":{"havePermission":%s}}}""".formatted(canEdit))));
    }

    @Test
    void leJetonNEstJamaisRenvoye() {
        api().body(jiraBody("SECRET-PAT-123")).put("/api/settings/jira").then().statusCode(200)
                .body("tokenSet", equalTo(true)).body(not(containsString("SECRET-PAT-123")));
        api().get("/api/settings").then().statusCode(200)
                .body("jira.tokenSet", equalTo(true))
                .body("jira.insecureHttp", equalTo(true))
                .body("jira.token", nullValue())
                .body(not(containsString("SECRET-PAT-123")));
    }

    @Test
    void sansJetonLeJetonExistantEstConserve() {
        api().body(jiraBody("PAT-1")).put("/api/settings/jira").then().statusCode(200);
        api().body(jiraBody(null)).put("/api/settings/jira").then().statusCode(200).body("tokenSet", equalTo(true));
    }

    @Test
    void httpSansConfirmationRefuse() {
        var body = jiraBody("PAT");
        body.put("acceptInsecureHttp", false);
        api().body(body).put("/api/settings/jira").then().statusCode(422)
                .body("code", equalTo("VALIDATION_FAILED")).body("userMessage", containsString("http"));
    }

    @Test
    void testDeConnexionOk() {
        stubHealthyJira("9.12.3", true);
        api().body(jiraBody("PAT")).put("/api/settings/jira").then().statusCode(200);
        api().post("/api/settings/jira/test").then().statusCode(200)
                .body("result", equalTo("OK")).body("serverVersion", equalTo("9.12.3"))
                .body("userMessage", notNullValue())
                // 012 : le projet vérifié est nommé ; le projet courant sans clé prend la clé par défaut
                .body("projectKey", equalTo("PROJ"));
        api().get("/api/settings").then().body("jira.lastTest.result", equalTo("OK"));
    }

    @Test
    void cleParDefautFacultative() {
        stubHealthyJira("9.12.3", true);
        var body = jiraBody("PAT");
        body.remove("defaultProjectKey");
        api().body(body).put("/api/settings/jira").then().statusCode(200).body("defaultProjectKey", nullValue());
        api().post("/api/settings/jira/test").then().statusCode(200)
                .body("result", equalTo("OK_NO_PROJECT")).body("projectKey", nullValue())
                .body("userMessage", containsString("réglages du projet"));
    }

    @Test
    void testDeConnexionCauses() {
        api().body(jiraBody("PAT")).put("/api/settings/jira").then().statusCode(200);

        stubHealthyJira("8.5.0", true);
        api().post("/api/settings/jira/test").then().body("result", equalTo("UNSUPPORTED_VERSION"));

        stubHealthyJira("9.12.3", false);
        api().post("/api/settings/jira/test").then().body("result", equalTo("MISSING_PERMISSION"));

        jira.stubFor(get("/rest/api/2/project/PROJ").willReturn(aResponse().withStatus(404)));
        api().post("/api/settings/jira/test").then().body("result", equalTo("PROJECT_NOT_FOUND"));

        jira.stubFor(get("/rest/api/2/myself").willReturn(aResponse().withStatus(401)));
        api().post("/api/settings/jira/test").then().body("result", equalTo("TOKEN_REJECTED"));

        jira.stubFor(get("/rest/api/2/serverInfo").willReturn(aResponse().withStatus(503)));
        api().post("/api/settings/jira/test").then().body("result", equalTo("UNREACHABLE"));
    }

    @Test
    void sansConsentementAucunAppelAJira() {
        api().body(jiraBody("PAT")).put("/api/settings/jira").then().statusCode(200);
        consent("JIRA", false);
        api().post("/api/settings/jira/test").then().body("result", equalTo("CONSENT_REQUIRED"));
        jira.verify(0, com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor(
                com.github.tomakehurst.wiremock.client.WireMock.anyUrl()));
        api().get("/api/settings").then().body("jira.consent", equalTo(false));
    }

    @Test
    void testSansConfigurationJira() {
        api().post("/api/settings/jira/test").then().statusCode(409).body("code", equalTo("JIRA_NOT_CONFIGURED"));
    }

    @Test
    void connexionCopilotPuisChoixDuModele() {
        String flowId = api().post("/api/settings/copilot/device-flow").then().statusCode(200)
                .body("authorizationUrl", notNullValue())
                .body("requestedScopes", hasItem("read:user"))
                .body("expiresAt", notNullValue())
                .extract().path("flowId");
        api().get("/api/settings/copilot/device-flow/" + flowId).then().body("state", equalTo("PENDING"));
        api().get("/api/settings/copilot/device-flow/" + flowId).then()
                .body("state", equalTo("CONNECTED")).body("secretPersistence", equalTo("KEYCHAIN"));
        api().get("/api/settings").then().body("copilot.state", equalTo("CONNECTED"))
                .body("copilot.model", notNullValue());

        api().body(Map.of("model", "inexistant")).put("/api/settings/copilot/model").then().statusCode(422)
                .body("code", equalTo("MODEL_UNAVAILABLE"));
        String other = api().get("/api/settings").then().extract().path("copilot.availableModels[0]");
        api().body(Map.of("model", other)).put("/api/settings/copilot/model").then().statusCode(204);
        api().get("/api/settings").then().body("copilot.model", equalTo(other));

        // 008 : capacité, coût et place de la connaissance de chaque modèle (modèles simulés)
        api().get("/api/settings").then()
                .body("copilot.models.id", contains("model-a", "model-b", "model-c"))
                .body("copilot.models[0].promptTokens", nullValue())
                .body("copilot.models[0].multiplier", nullValue())
                .body("copilot.models[0].knowledgeChars", equalTo(35_000))
                .body("copilot.models[1].promptTokens", equalTo(32_000))
                .body("copilot.models[1].multiplier", equalTo(0.33f))
                .body("copilot.models[2].name", equalTo("Grand modèle"))
                .body("copilot.models[2].contextTokens", equalTo(200_000))
                .body("copilot.models[2].knowledgeChars", equalTo(162_000))
                .body("copilot.knowledgeChars", equalTo(35_000));
        api().body(Map.of("model", "model-c")).put("/api/settings/copilot/model").then().statusCode(204);
        api().get("/api/settings").then().body("copilot.knowledgeChars", equalTo(162_000));

        api().delete("/api/settings/copilot").then().statusCode(204);
        api().get("/api/settings").then().body("copilot.state", equalTo("NOT_CONFIGURED"))
                .body("copilot.model", nullValue());
    }

    /** T115 : carte « Modèle de recherche » : état, dossier de remplacement chargé aussitôt, motif d'un refus. */
    @Test
    void modeleDeRechercheRemplaceParUnDossier() {
        String folder = java.nio.file.Path.of("target/test-model").toAbsolutePath().toString();
        try {
            api().body(java.util.Map.of("folder", folder, "pooling", "MEAN", "queryPrefix", "query: ", "passagePrefix",
                    "passage: ")).put("/api/settings/embedding").then().statusCode(200)
                    .body("state", org.hamcrest.Matchers.equalTo("READY")).body("defaultModel", org.hamcrest.Matchers.equalTo(false))
                    .body("folder", org.hamcrest.Matchers.equalTo(folder)).body("modelId", org.hamcrest.Matchers.notNullValue());
            api().get("/api/settings/embedding").then().statusCode(200).body("pooling", org.hamcrest.Matchers.equalTo("MEAN"))
                    .body("queryPrefix", org.hamcrest.Matchers.equalTo("query: "));
            api().body(java.util.Map.of("folder", "relatif/modele")).put("/api/settings/embedding").then().statusCode(422)
                    .body("userMessage", org.hamcrest.Matchers.containsString("chemin absolu"));
        } finally {
            api().body(java.util.Map.of("folder", "")).put("/api/settings/embedding").then().statusCode(200)
                    .body("defaultModel", org.hamcrest.Matchers.equalTo(true));
        }
    }

    @Test
    void consentementCopilotEnregistre() {
        consent("COPILOT", true);
        api().get("/api/settings").then().body("copilot.consent", equalTo(true));
        consent("COPILOT", false);
        api().get("/api/settings").then().body("copilot.consent", equalTo(false));
    }

    @Test
    void consentementServiceInconnuOuCorpsVide() {
        api().body(Map.of("granted", true)).put("/api/consents/AUTRE").then().statusCode(404);
        api().body("{}").put("/api/consents/JIRA").then().statusCode(422);
    }
}
