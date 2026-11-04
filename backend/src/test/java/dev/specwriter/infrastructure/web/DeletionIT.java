package dev.specwriter.infrastructure.web;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static dev.specwriter.support.LocalApi.api;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.specwriter.application.specification.OperationRegistry;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.Specs;
import dev.specwriter.domain.specification.VersionOrigin;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * T036 : suppression d'une version et d'une spécification par l'API (FR-014 à FR-019 de 002) :
 * disparition de la liste, de l'historique, de l'export et de la recherche, numéros jamais
 * réattribués, refus pendant une opération de l'IA, et aucune requête vers Jira (SC-005).
 */
@QuarkusTest
class DeletionIT {

    static WireMockServer jira;

    @Inject
    SpecificationRepository versions;
    @Inject
    LinkedTicketRepository tickets;
    @Inject
    OperationRegistry operations;

    @BeforeAll
    static void start() {
        jira = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        jira.start();
    }

    @AfterAll
    static void stop() {
        api().delete("/api/settings/jira");
        jira.stop();
    }

    static List<String> found(String q) {
        return api().queryParam("q", q).get("/api/search").then().statusCode(200).extract().jsonPath().getList("items.id");
    }

    @Test
    void versionPuisSpecificationSupprimeesSansAppelerJira() {
        SessionResourceIT.copilotConsent(true);
        api().body(Map.of("serverUrl", "http://127.0.0.1:" + jira.port(), "defaultProjectKey", "PROJ", "acceptInsecureHttp",
                true, "token", "TEST-JIRA-PAT")).put("/api/settings/jira").then().statusCode(200);
        SearchAndJiraResourceIT.consent("JIRA", true);

        JsonPath created = SessionResourceIT.create("IT-OK suppressionlocale");
        String id = created.getString("id");
        for (String q : created.<String>getList("currentBatch.id")) {
            api().body(Map.of("skip", true)).put("/api/sessions/" + id + "/questions/" + q + "/answer").then().statusCode(200);
        }
        api().post("/api/sessions/" + id + "/conclude").then().statusCode(200);
        api().post("/api/sessions/" + id + "/versions").then().statusCode(201).body("number", equalTo(1));
        UUID sessionId = UUID.fromString(id);
        versions.save(new SpecificationVersion(UUID.randomUUID(), sessionId, 2, Specs.valid(), "fp-v2", VersionOrigin.AI_EDIT,
                "# Version deux\n\nmotunique", Instant.now()));
        tickets.save(new LinkedJiraTicket(sessionId, "PROJ-77", "http://127.0.0.1:" + jira.port() + "/browse/PROJ-77",
                "Story", 2, Instant.now(), "h", Instant.now(), Instant.now(), LinkedJiraTicket.SyncState.IN_SYNC));
        assertThat(found("motunique")).contains(id);
        jira.resetRequests();

        // version liée supprimée : le lien suit la dernière restante, qui n'est pas celle de Jira
        api().delete("/api/sessions/" + id + "/versions/2").then().statusCode(204);
        api().get("/api/sessions/" + id + "/versions").then().statusCode(200).body("number", equalTo(List.of(1)));
        api().get("/api/sessions/" + id + "/versions/2").then().statusCode(404);
        api().get("/api/sessions/" + id + "/versions/2/export").then().statusCode(404);
        api().get("/api/sessions/" + id).then().statusCode(200).body("latestVersion", equalTo(1))
                .body("linkedTicket.linkedVersionNumber", equalTo(1)).body("linkedTicket.syncState", equalTo("LOCAL_AHEAD"));
        assertThat(found("motunique")).doesNotContain(id);
        api().delete("/api/sessions/" + id + "/versions/2").then().statusCode(404);

        // refus pendant une opération de l'IA
        try (OperationRegistry.Operation running = operations.begin(sessionId)) {
            api().delete("/api/sessions/" + id + "/versions/1").then().statusCode(409)
                    .body("code", equalTo("OPERATION_IN_PROGRESS"));
            api().delete("/api/sessions/" + id).then().statusCode(409).body("code", equalTo("OPERATION_IN_PROGRESS"));
        }

        // dernière version : la session reste, prête à générer ; le numéro 2 n'est pas réattribué
        api().delete("/api/sessions/" + id + "/versions/1").then().statusCode(204);
        api().get("/api/sessions/" + id).then().statusCode(200).body("state", equalTo("READY_TO_GENERATE"))
                .body("need", equalTo(created.getString("need")));
        api().get("/api/sessions/" + id + "/versions").then().statusCode(200).body("number", equalTo(List.of()));
        api().post("/api/sessions/" + id + "/versions").then().statusCode(201).body("number", equalTo(3));

        // spécification entière : session, réponses, versions et lien
        api().delete("/api/sessions/" + id).then().statusCode(204);
        api().get("/api/sessions/" + id).then().statusCode(404);
        api().get("/api/sessions").then().statusCode(200).body("id", not(hasItem(id)));
        assertThat(found("suppressionlocale")).doesNotContain(id);
        assertThat(tickets.findBySession(sessionId)).isEmpty();

        // seules les recherches de vérification du test (« text ~ ») ont interrogé Jira
        assertThat(jira.getAllServeEvents()).as("aucune requête vers Jira (SC-005)")
                .extracting(e -> e.getRequest().getMethod() + " " + e.getRequest().getUrl())
                .allMatch(r -> r.startsWith("GET /rest/api/2/search?jql=project+%3D+PROJ+AND+text+~"));
    }
}
