package dev.specwriter.infrastructure.web;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dev.specwriter.support.LocalApi.api;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T013 : {@code /api/projects*} et {@code /api/sessions/{id}/project} (contracts/openapi.yaml de 002, US1). */
@QuarkusTest
class ProjectResourceIT {

    static WireMockServer jira;

    @Inject
    SessionRepository sessions;

    String marker;

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
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/project/FACT")).willReturn(okJson("{\"key\":\"FACT\"}")));
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/project/NOPE")).willReturn(notFound()));
        api().body(Map.of("serverUrl", "http://127.0.0.1:" + jira.port(), "defaultProjectKey", "PROJ", "acceptInsecureHttp",
                true, "token", "TEST-JIRA-PAT")).put("/api/settings/jira").then().statusCode(200);
        api().body(Map.of("granted", true)).put("/api/consents/JIRA").then().statusCode(204);
        marker = UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void backToDefault() {
        api().body(Map.of("projectId", Project.DEFAULT_ID.toString())).put("/api/projects/current").then().statusCode(204);
    }

    JsonPath create(String name, String key, int status) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        if (key != null) {
            body.put("jiraProjectKey", key);
        }
        return api().body(body).post("/api/projects").then().statusCode(status).extract().jsonPath();
    }

    Session sessionIn(String projectId) {
        Session s = Session.start(UUID.randomUUID(), UUID.fromString(projectId),
                Need.of("Les factures du mois " + marker + " doivent être envoyées aux clients avant le cinq."), null,
                Instant.parse("2026-11-03T09:00:00Z"));
        sessions.save(s, Questionnaire.empty());
        return s;
    }

    @Test
    void creationListeEtModification() {
        JsonPath created = create("Facturation " + marker, "FACT", 201);
        String id = created.getString("id");
        assertThat(created.getString("effectiveJiraProjectKey")).isEqualTo("FACT");
        assertThat(created.getString("jiraKeyWarning")).isNull();
        assertThat(created.getInt("sessionCount")).isZero();

        api().get("/api/projects").then().statusCode(200)
                .body("find { it.id == '" + id + "' }.name", equalTo("Facturation " + marker))
                .body("find { it.id == '" + Project.DEFAULT_ID + "' }.effectiveJiraProjectKey", equalTo("PROJ"));

        api().body(Map.of("name", "Facturation clients " + marker, "description", "Factures et avoirs"))
                .put("/api/projects/" + id).then().statusCode(200)
                .body("name", equalTo("Facturation clients " + marker))
                .body("description", equalTo("Factures et avoirs"))
                .body("jiraProjectKey", nullValue())
                .body("effectiveJiraProjectKey", equalTo("PROJ"));

        assertThat(create("FACTURATION CLIENTS " + marker, null, 409).getString("code")).isEqualTo("PROJECT_NAME_TAKEN");
        api().body(Map.of("name", "")).post("/api/projects").then().statusCode(422);
        api().body(Map.of("name", "Paie " + marker, "jiraProjectKey", "fact")).post("/api/projects").then().statusCode(422);
        api().body(Map.of("name", "x")).put("/api/projects/" + UUID.randomUUID()).then().statusCode(404)
                .body("code", equalTo("PROJECT_NOT_FOUND"));
        api().delete("/api/projects/" + id + "?sessions=DELETE").then().statusCode(204);
    }

    @Test
    void cleJiraIntrouvableSignaleeSansBloquer() {
        JsonPath created = create("Paie " + marker, "NOPE", 201);
        assertThat(created.getString("jiraKeyWarning")).contains("NOPE");
        String id = created.getString("id");
        api().get("/api/projects").then().body("find { it.id == '" + id + "' }.jiraProjectKey", equalTo("NOPE"));
        api().body(Map.of("name", "Paie " + marker, "jiraProjectKey", "FACT")).put("/api/projects/" + id).then()
                .statusCode(200).body("jiraKeyWarning", nullValue());
        api().body(Map.of("name", "Paie " + marker, "jiraProjectKey", "NOPE")).put("/api/projects/" + id).then()
                .statusCode(200).body("jiraKeyWarning", notNullValue());
        api().delete("/api/projects/" + id + "?sessions=DELETE").then().statusCode(204);
    }

    @Test
    void listeEtRechercheFiltreesSurLeProjetCourant() {
        String id = create("Facturation " + marker, "FACT", 201).getString("id");
        Session inDefault = sessionIn(Project.DEFAULT_ID.toString());
        Session inFacturation = sessionIn(id);

        api().get("/api/projects/current").then().statusCode(200).body("id", equalTo(Project.DEFAULT_ID.toString()));
        api().get("/api/sessions").then().statusCode(200)
                .body("id", hasItem(inDefault.id().toString()))
                .body("id", not(hasItem(inFacturation.id().toString())));
        api().queryParam("q", marker).get("/api/search").then().statusCode(200)
                .body("items.id", hasItem(inDefault.id().toString()))
                .body("items.id", not(hasItem(inFacturation.id().toString())));

        api().body(Map.of("projectId", id)).put("/api/projects/current").then().statusCode(204);
        api().get("/api/projects/current").then().body("id", equalTo(id)).body("sessionCount", equalTo(1));
        api().get("/api/sessions").then()
                .body("id", hasItem(inFacturation.id().toString()))
                .body("id", not(hasItem(inDefault.id().toString())));
        api().body(Map.of("projectId", UUID.randomUUID().toString())).put("/api/projects/current").then().statusCode(404);

        // déplacement d'une spécification
        api().body(Map.of("projectId", id)).put("/api/sessions/" + inDefault.id() + "/project").then().statusCode(204);
        api().get("/api/sessions").then().body("id", hasItem(inDefault.id().toString()));
        api().body(Map.of("projectId", UUID.randomUUID().toString())).put("/api/sessions/" + inDefault.id() + "/project")
                .then().statusCode(404).body("code", equalTo("PROJECT_NOT_FOUND"));

        // suppression en déplaçant vers le projet par défaut
        api().delete("/api/projects/" + id + "?sessions=MOVE").then().statusCode(422);
        api().delete("/api/projects/" + id + "?sessions=MOVE&targetProjectId=" + Project.DEFAULT_ID).then().statusCode(204);
        assertThat(sessions.find(inFacturation.id()).orElseThrow().projectId()).isEqualTo(Project.DEFAULT_ID);
        assertThat(sessions.find(inDefault.id()).orElseThrow().projectId()).isEqualTo(Project.DEFAULT_ID);
        api().get("/api/projects/current").then().body("id", equalTo(Project.DEFAULT_ID.toString()));
        api().delete("/api/projects/" + id + "?sessions=DELETE").then().statusCode(404);
        api().delete("/api/projects/" + Project.DEFAULT_ID + "?sessions=TOUT").then().statusCode(422);
        sessions.delete(inFacturation.id());
        sessions.delete(inDefault.id());
    }
}
