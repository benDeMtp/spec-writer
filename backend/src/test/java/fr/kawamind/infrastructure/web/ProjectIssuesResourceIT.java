package fr.kawamind.infrastructure.web;

import static fr.kawamind.support.LocalApi.api;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.notNullValue;

import fr.kawamind.domain.jira.TicketLink;
import fr.kawamind.domain.knowledge.KnowledgeItem;
import fr.kawamind.domain.knowledge.KnowledgeKind;
import fr.kawamind.domain.port.KnowledgeIndex;
import fr.kawamind.domain.port.TicketLinkRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** T012 : {@code GET /api/projects/{id}/jira-issues} (017, contracts/rest-api.md). */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class ProjectIssuesResourceIT {

    static final String DEFAULT = "00000000-0000-0000-0000-000000000001";

    @Inject
    KnowledgeIndex index;
    @Inject
    TicketLinkRepository links;

    @AfterEach
    void nettoyer() {
        api().delete("/api/settings/jira");
    }

    /** T005 (022, contracts/rest-api.md) : premier niveau, sous-tâches et parent d'une orpheline. */
    @Test
    void lesSousTachesSuiventLeurParent() {
        UUID project = UUID.fromString(DEFAULT);
        Instant at = Instant.parse("2099-01-01T00:00:00Z");
        QuarkusTransaction.requiringNew().run(() -> {
            index.save(KnowledgeItem.ticket(project, "HIER-1", "Story", "", "En cours", "indeterminate", "Story", at, at),
                    null, null);
            index.save(KnowledgeItem.ticket(project, "HIER-2", "Sous-tâche", "", "À faire", "new", "Sous-tâche",
                    at.minusSeconds(60), at), null, null);
            index.save(KnowledgeItem.ticket(project, "HIER-3", "Orpheline", "", "À faire", "new", "Sous-tâche",
                    at.minusSeconds(120), at), null, null);
            links.replace(project, "HIER-2", List.of(TicketLink.parent("HIER-1")));
            links.replace(project, "HIER-3", List.of(TicketLink.parent("HORS-9")));
        });
        try {
            api().body(Map.of("serverUrl", "http://127.0.0.1:9", "defaultProjectKey", "PROJ", "acceptInsecureHttp", true,
                    "token", "TEST-JIRA-PAT")).put("/api/settings/jira").then().statusCode(200);
            api().queryParam("size", 2).get("/api/projects/" + DEFAULT + "/jira-issues").then().statusCode(200)
                    .body("issues.key", contains("HIER-1", "HIER-3"))
                    .body("issues[0].parentKey", nullValue())
                    .body("issues[0].subtasks.key", contains("HIER-2"))
                    .body("issues[0].subtasks[0].parentKey", nullValue())
                    .body("issues[0].subtasks[0].subtasks", empty())
                    .body("issues[0].subtasks[0].statusCategory", equalTo("new"))
                    .body("issues[1].parentKey", equalTo("HORS-9"))
                    .body("issues[1].subtasks", empty())
                    .body("issues.key", not(hasItem("HIER-2")));
        } finally {
            QuarkusTransaction.requiringNew().run(() -> {
                for (String key : List.of("HIER-1", "HIER-2", "HIER-3")) {
                    index.delete(project, new KnowledgeIndex.Key(KnowledgeKind.TICKET, key, 0));
                    links.replace(project, key, List.of());
                }
            });
        }
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
