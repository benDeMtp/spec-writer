package fr.kawamind.infrastructure.web;

import static fr.kawamind.support.LocalApi.api;
import static fr.kawamind.support.LocalApi.cookie;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T020 (023, contracts/rest-api.md) : {@code /api/projects/{id}/task-workflow}. */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class TaskWorkflowResourceIT {

    String projectId;

    @BeforeEach
    void setUp() {
        projectId = api().body(Map.of("name", "Suivi " + UUID.randomUUID())).post("/api/projects").then()
                .statusCode(201).extract().path("id");
    }

    @AfterEach
    void deleteProject() {
        api().delete("/api/projects/" + projectId + "/indexations/current").then().statusCode(204);
        api().delete("/api/projects/" + projectId + "?sessions=DELETE").then().statusCode(204);
    }

    String path() {
        return "/api/projects/" + projectId + "/task-workflow";
    }

    @Test
    void lireEnregistrerRetirer() {
        api().get(path()).then().statusCode(200).body("arrivalStatus", nullValue()).body("finishedStatuses.size()",
                equalTo(0));
        api().body(Map.of("arrivalStatus", "Réalisée", "finishedStatuses", List.of("En cours de test"))).put(path())
                .then().statusCode(200).body("arrivalStatus", equalTo("Réalisée"))
                .body("finishedStatuses", contains("Réalisée", "En cours de test"))
                .body("known.name", contains("Réalisée", "En cours de test")).body("known[0].seen", equalTo(false));
        api().get(path()).then().statusCode(200).body("arrivalStatus", equalTo("Réalisée"));
        api().delete(path()).then().statusCode(204);
        api().get(path()).then().statusCode(200).body("arrivalStatus", nullValue());
    }

    @Test
    void refus() {
        api().body(Map.of("arrivalStatus", "x".repeat(101), "finishedStatuses", List.of())).put(path()).then()
                .statusCode(422).body("code", equalTo("VALIDATION_FAILED"));
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            many.add("S" + i);
        }
        api().body(Map.of("arrivalStatus", "Réalisée", "finishedStatuses", many)).put(path()).then().statusCode(422);
        // une écriture sans Origin locale est refusée, comme les autres
        given().cookie("sw_launch", cookie()).contentType(ContentType.JSON)
                .body(Map.of("arrivalStatus", "Réalisée", "finishedStatuses", List.of())).put(path()).then()
                .statusCode(403);
        api().get("/api/projects/" + UUID.randomUUID() + "/task-workflow").then().statusCode(404)
                .body("code", equalTo("PROJECT_NOT_FOUND"));
    }
}
