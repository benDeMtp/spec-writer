package fr.kawamind.infrastructure.web;

import static fr.kawamind.support.LocalApi.api;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import fr.kawamind.domain.interview.SourceUpdate;
import fr.kawamind.domain.knowledge.KnowledgeItem;
import fr.kawamind.domain.port.KnowledgeIndex;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.SourceUpdateRepository;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectName;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Need;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.specification.SourceRef;
import fr.kawamind.domain.specification.SpecificationVersion;
import fr.kawamind.domain.specification.Specs;
import fr.kawamind.domain.specification.VersionOrigin;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 021, US6 : « à reporter » d'une spécification ; une source désignée doit être connue. Aucune
 * route n'écrit dans les sources (FR-030) : la ressource n'a ni client Jira ni client Confluence.
 */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class SourceUpdateResourceIT {

    static final Instant T0 = Instant.parse("2026-11-20T09:00:00Z");
    static final String PAGE = "https://wiki/pages/42";

    @Inject
    ProjectRepository projects;

    @Inject
    SessionRepository sessions;

    @Inject
    SpecificationRepository versions;

    @Inject
    KnowledgeIndex index;

    @Inject
    SourceUpdateRepository updates;

    @Inject
    fr.kawamind.domain.port.InterviewRepository interviews;

    @Test
    void listerClorePuisRefuserUneSourceInconnue() {
        Project p = Project.create(UUID.randomUUID(), ProjectName.of("À reporter " + UUID.randomUUID()), null, null, T0);
        projects.save(p);
        UUID[] created = new UUID[1];
        try {
            index.save(KnowledgeItem.docSection(p.id(), PAGE, 0, "Règles de facturation", "Plafond.", null, T0, T0), null,
                    "m");
            Session s = Session.start(UUID.randomUUID(), p.id(),
                    Need.of("Le gestionnaire doit pouvoir émettre un avoir partiel sur une facture."), "Avoirs", T0);
            sessions.save(s, Questionnaire.empty());
            created[0] = s.id();
            versions.save(new SpecificationVersion(UUID.randomUUID(), s.id(), 1, Specs.valid(), "fp",
                    VersionOrigin.GENERATED, "# v1", T0));
            String current = index.fingerprint(p.id(), SourceRef.Kind.DOC_PAGE.knowledgeKinds(), PAGE).orElseThrow();
            SourceUpdate open = SourceUpdate.open(UUID.randomUUID(), s.id(), null, null, SourceRef.Kind.DOC_PAGE, PAGE,
                    "Règles de facturation", current, "Plafonné au montant TTC facturé.", T0);
            updates.save(open);
            updates.save(SourceUpdate.open(UUID.randomUUID(), s.id(), null, null, SourceRef.Kind.DOC_PAGE, PAGE,
                    "Règles de facturation", "ancienne", "Autre décision.", T0.plusSeconds(1)));

            api().get("/api/sessions/" + s.id() + "/source-updates").then().statusCode(200).body("$", hasSize(2))
                    .body("[0].source.title", equalTo("Règles de facturation")).body("[0].source.link", equalTo(PAGE))
                    .body("[0].text", equalTo("Plafonné au montant TTC facturé.")).body("[0].state", equalTo("OPEN"))
                    .body("[1].state", equalTo("SOURCE_CHANGED"));
            api().post("/api/sessions/" + s.id() + "/source-updates/" + open.id() + "/close").then().statusCode(204);
            api().get("/api/sessions/" + s.id() + "/source-updates").then().statusCode(200).body("$", hasSize(1));
            api().post("/api/sessions/" + UUID.randomUUID() + "/source-updates/" + open.id() + "/close").then()
                    .statusCode(404);

            // une source désignée en séance doit être dans la connaissance du projet
            String id = api().body(Map.of()).post("/api/sessions/" + s.id() + "/interviews").then().statusCode(201)
                    .extract().path("id");
            Map<String, Object> remark = new HashMap<>();
            remark.put("sessionId", s.id().toString());
            remark.put("origin", "REMARK");
            remark.put("text", "À reporter dans la page.");
            remark.put("source", Map.of("kind", "DOC_PAGE", "ref", "https://wiki/pages/99"));
            api().body(remark).post("/api/interviews/" + id + "/points").then().statusCode(422);
            remark.put("source", Map.of("kind", "DOC_PAGE", "ref", PAGE));
            api().body(remark).post("/api/interviews/" + id + "/points").then().statusCode(201)
                    .body("source.ref", equalTo(PAGE));
        } finally {
            if (created[0] != null) {
                interviews.forSession(created[0]).forEach(i -> interviews.delete(i.id()));
                sessions.delete(created[0]);
            }
            projects.delete(p.id());
        }
    }

}
