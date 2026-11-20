package fr.kawamind.infrastructure.web;

import static fr.kawamind.support.LocalApi.api;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** T012 : {@code GET /api/projects/{id}/jira-issues} (017, contracts/rest-api.md). */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class ProjectIssuesResourceIT {

    static final String DEFAULT = "00000000-0000-0000-0000-000000000001";

    @AfterEach
    void nettoyer() {
        api().delete("/api/settings/jira");
    }

    @Test
    void sansJiraLaVueEstRefusee() {
        api().delete("/api/settings/jira");
        api().get("/api/projects/" + DEFAULT + "/jira-issues").then().statusCode(409)
                .body("code", equalTo("JIRA_NOT_CONFIGURED"));
    }

    @Test
    void pageDeLIndexSansAppelAJira() {
        // un serveur injoignable : la vue ne l'appelle jamais (SC-002)
        api().body(Map.of("serverUrl", "http://127.0.0.1:9", "defaultProjectKey", "PROJ", "acceptInsecureHttp", true,
                "token", "TEST-JIRA-PAT")).put("/api/settings/jira").then().statusCode(200);
        api().queryParam("sort", "sync").queryParam("dir", "asc").get("/api/projects/" + DEFAULT + "/jira-issues")
                .then().statusCode(200).body("jiraProjectKey", equalTo("PROJ")).body("startAt", equalTo(0))
                .body("total", notNullValue()).body("issues", notNullValue());
    }

    @Test
    void triInconnuEtProjetInconnu() {
        api().body(Map.of("serverUrl", "http://127.0.0.1:9", "defaultProjectKey", "PROJ", "acceptInsecureHttp", true,
                "token", "TEST-JIRA-PAT")).put("/api/settings/jira").then().statusCode(200);
        api().queryParam("sort", "title").get("/api/projects/" + DEFAULT + "/jira-issues").then().statusCode(422)
                .body("code", equalTo("VALIDATION_FAILED"));
        api().queryParam("size", 500).get("/api/projects/" + DEFAULT + "/jira-issues").then().statusCode(422);
        api().get("/api/projects/" + UUID.randomUUID() + "/jira-issues").then().statusCode(404)
                .body("code", equalTo("PROJECT_NOT_FOUND"));
    }
}
