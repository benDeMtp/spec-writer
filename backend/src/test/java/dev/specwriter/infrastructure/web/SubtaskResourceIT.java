package dev.specwriter.infrastructure.web;

import static dev.specwriter.support.LocalApi.api;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.jira.TicketLink;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.TicketLinkRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T126 : rubrique des sous-tâches d'une version (contrat openapi.yaml, research.md R29). */
@QuarkusTest
class SubtaskResourceIT {

    static final Instant T = Instant.parse("2026-10-01T10:00:00Z");

    @Inject
    SessionRepository sessions;
    @Inject
    LinkedTicketRepository tickets;
    @Inject
    TicketLinkRepository links;
    @Inject
    KnowledgeIndex index;

    String generated() {
        SessionResourceIT.copilotConsent(true);
        JsonPath created = SessionResourceIT.create("IT-OK sous-tâches de la story");
        String id = created.getString("id");
        for (String q : created.<String>getList("currentBatch.id")) {
            api().body(Map.of("skip", true)).put("/api/sessions/" + id + "/questions/" + q + "/answer").then().statusCode(200);
        }
        api().post("/api/sessions/" + id + "/conclude").then().statusCode(200);
        api().post("/api/sessions/" + id + "/versions").then().statusCode(201);
        return id;
    }

    @Test
    void rubriqueDesSousTachesPuisAucuneSansTicketLie() {
        String id = generated();
        api().get("/api/sessions/" + id + "/versions/1/subtasks").then().statusCode(204);

        UUID sessionId = UUID.fromString(id);
        UUID project = sessions.find(sessionId).orElseThrow().projectId();
        String story = "SUB-" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000);
        tickets.save(new LinkedJiraTicket(sessionId, story, "http://127.0.0.1/browse/" + story, "Story", 1, T, "h", T, T,
                LinkedJiraTicket.SyncState.IN_SYNC));
        api().get("/api/sessions/" + id + "/versions/1/subtasks").then().statusCode(204);

        links.replace(project, story, List.of(TicketLink.subtask(story + "1"), TicketLink.subtask(story + "2")));
        index.save(KnowledgeItem.ticket(project, story + "1", "Écran de paiement", "Payer le panier", "À faire", "new",
                "Sous-tâche", T, T), null, null);
        api().get("/api/sessions/" + id + "/versions/1/subtasks").then().statusCode(200)
                .body("storyKey", equalTo(story))
                .body("subtasks.key", hasItems(story + "1", story + "2"))
                .body("subtasks.find { it.key == '" + story + "1' }.title", equalTo("Écran de paiement"))
                .body("subtasks.find { it.key == '" + story + "1' }.status", equalTo("À faire"))
                .body("subtasks.find { it.key == '" + story + "1' }.statusFresh", equalTo(false))
                .body("subtasks.find { it.key == '" + story + "2' }.state", equalTo("UNMATCHED"));
    }

    @Test
    void sessionOuVersionInconnue() {
        api().get("/api/sessions/" + UUID.randomUUID() + "/versions/1/subtasks").then().statusCode(404);
        String id = generated();
        api().get("/api/sessions/" + id + "/versions/9/subtasks").then().statusCode(404);
    }
}
