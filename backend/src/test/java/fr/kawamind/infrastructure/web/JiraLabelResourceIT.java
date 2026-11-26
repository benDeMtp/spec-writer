package fr.kawamind.infrastructure.web;

import static fr.kawamind.support.LocalApi.api;
import static fr.kawamind.support.LocalApi.cookie;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T016 (024, contracts/rest-api.md) : {@code /api/projects/{id}/jira-label}. */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class JiraLabelResourceIT {

    String projectId;

    @BeforeEach
    void setUp() {
        projectId = api().body(Map.of("name", "Label " + UUID.randomUUID())).post("/api/projects").then()
                .statusCode(201).extract().path("id");
    }

    @AfterEach
    void deleteProject() {
        api().delete("/api/projects/" + projectId + "/indexations/current").then().statusCode(204);
        api().delete("/api/projects/" + projectId + "?sessions=DELETE").then().statusCode(204);
    }

    String path() {
        return "/api/projects/" + projectId + "/jira-label";
    }

    @Test
    void lireEnregistrerRetirer() {
        api().get(path()).then().statusCode(200).body("label", nullValue());
        api().body(Map.of("label", "spec-writer")).put(path()).then().statusCode(200).body("label", equalTo("spec-writer"));
        api().get(path()).then().statusCode(200).body("label", equalTo("spec-writer"));
        api().delete(path()).then().statusCode(204);
        api().get(path()).then().statusCode(200).body("label", nullValue());
    }

    @Test
    void refus() {
        api().body(Map.of("label", "spec writer")).put(path()).then().statusCode(422)
                .body("code", equalTo("VALIDATION_FAILED"));
        given().cookie("sw_launch", cookie()).contentType(ContentType.JSON).body(Map.of("label", "spec-writer"))
                .put(path()).then().statusCode(403);
        api().get("/api/projects/" + UUID.randomUUID() + "/jira-label").then().statusCode(404)
                .body("code", equalTo("PROJECT_NOT_FOUND"));
    }
}
