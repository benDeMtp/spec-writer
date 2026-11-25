package fr.kawamind.infrastructure.web;

import static fr.kawamind.support.LocalApi.api;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import fr.kawamind.domain.port.InterviewRepository;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectName;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Need;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.specification.Anchor;
import fr.kawamind.domain.specification.Assumption;
import fr.kawamind.domain.specification.SpecificationVersion;
import fr.kawamind.domain.specification.Specs;
import fr.kawamind.domain.specification.VersionOrigin;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 021, contracts/rest-api.md : « Faire le point », points, curseur, fin de séance. */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class InterviewResourceIT {

    static final Instant T0 = Instant.parse("2026-11-20T09:00:00Z");

    @Inject
    ProjectRepository projects;

    @Inject
    SessionRepository sessions;

    @Inject
    SpecificationRepository versions;

    @Inject
    InterviewRepository interviews;

    final List<UUID> createdProjects = new ArrayList<>();
    final List<UUID> createdSessions = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdSessions.forEach(id -> {
            interviews.forSession(id).forEach(i -> interviews.delete(i.id()));
            sessions.find(id).ifPresent(s -> sessions.delete(id));
        });
        createdProjects.forEach(id -> projects.find(id).ifPresent(p -> projects.delete(id)));
    }

    Session specification() {
        Project p = Project.create(UUID.randomUUID(), ProjectName.of("Séances " + UUID.randomUUID()), null, null, T0);
        projects.save(p);
        createdProjects.add(p.id());
        Session s = Session.start(UUID.randomUUID(), p.id(),
                Need.of("Le gestionnaire doit pouvoir émettre un avoir partiel sur une facture."), "Avoirs", T0);
        sessions.save(s, Questionnaire.empty());
        createdSessions.add(s.id());
        versions.save(new SpecificationVersion(UUID.randomUUID(), s.id(), 1,
                Specs.valid().withAssumptions(List.of(new Assumption("H-01", "Le montant est TTC.",
                        Assumption.Origin.TOOL_INFERENCE, null, new Anchor("UC-01", 2, false), null))),
                "fp", VersionOrigin.GENERATED, "# v1", T0));
        return s;
    }

    static Map<String, Object> point(UUID session, String origin, String code, String text, String outcome) {
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", session.toString());
        body.put("anchor", Map.of("element", "UC-01", "step", 2, "alternative", false));
        body.put("origin", origin);
        body.put("originCode", code);
        body.put("text", text);
        body.put("outcome", outcome);
        return body;
    }

    @Test
    void seanceDUneSpecification() {
        Session s = specification();
        String id = api().body(Map.of("participants", List.of("Julie M."))).post("/api/sessions/" + s.id() + "/interviews")
                .then().statusCode(201).body("state", equalTo("IN_PROGRESS"))
                .body("participants", equalTo(List.of("Julie M.")))
                .body("specs[0].name", equalTo("Avoirs")).body("specs[0].version", equalTo(1))
                .body("stops.label", hasItem("UC-01, étape 2"))
                .body("counts.pending", equalTo(1))
                .extract().path("id");

        // une seconde séance sur la même spécification est refusée
        api().body(Map.of()).post("/api/sessions/" + s.id() + "/interviews").then().statusCode(409)
                .body("code", equalTo("INTERVIEW_BUSY"));

        String pid = api().body(point(s.id(), "ASSUMPTION", "H-01", "", "CONFIRMED"))
                .post("/api/interviews/" + id + "/points").then().statusCode(201).body("outcome", equalTo("CONFIRMED"))
                .extract().path("id");
        api().body(point(s.id(), "ASSUMPTION", "H-01", "Plafonné au TTC.", "CHANGE"))
                .put("/api/interviews/" + id + "/points/" + pid).then().statusCode(200).body("text", equalTo("Plafonné au TTC."));
        String remark = api().body(point(s.id(), "REMARK", null, "Validation du manager", "CHANGE"))
                .post("/api/interviews/" + id + "/points").then().statusCode(201).extract().path("id");
        api().delete("/api/interviews/" + id + "/points/" + remark).then().statusCode(204);

        api().body(Map.of("sessionId", s.id().toString(), "element", "UC-01", "step", 2, "alternative", false))
                .put("/api/interviews/" + id + "/cursor").then().statusCode(204);
        api().get("/api/interviews/" + id).then().statusCode(200).body("cursor.anchor.element", equalTo("UC-01"))
                .body("counts.handled", equalTo(1)).body("counts.pending", equalTo(0))
                .body("specs[0].revisionAvailable", equalTo(false))
                .body("specs[0].revisionUnavailableReason", equalTo("La révision se propose une fois la séance terminée."));
        // la révision attend la fin de la séance
        api().post("/api/interviews/" + id + "/specs/" + s.id() + "/revision").then().statusCode(409)
                .body("code", equalTo("INTERVIEW_NOT_FINISHED"));

        api().get("/api/sessions/" + s.id() + "/interviews").then().statusCode(200).body("$", hasSize(1))
                .body("[0].specifications[0].name", equalTo("Avoirs")).body("[0].points", equalTo(1));

        api().get("/api/interviews/" + id + "/report").then().statusCode(200)
                .body("text", org.hamcrest.Matchers.startsWith("Point sur « Avoirs »"))
                .body("html", org.hamcrest.Matchers.containsString("<li>H-01 (UC-01, étape 2) — à modifier : Plafonné au TTC.</li>"));
        api().post("/api/interviews/" + id + "/finish").then().statusCode(200).body("state", equalTo("FINISHED"))
                .body("finishedAt", notNullValue());
        api().body(point(s.id(), "ASSUMPTION", "H-01", "", "CONFIRMED")).post("/api/interviews/" + id + "/points")
                .then().statusCode(409).body("code", equalTo("INTERVIEW_NOT_IN_PROGRESS"));
    }

    /** 021, US5 : préparer, modifier, démarrer ; points à traiter dans la liste des spécifications. */
    @Test
    void preparationDUneSeanceDePlusieursSpecifications() {
        UUID previous = fr.kawamind.application.project.CurrentProject.resolve(projects).id();
        Session a = specification();
        Session b = specification();
        try {
            api().body(Map.of("projectId", a.projectId().toString())).put("/api/projects/current").then().statusCode(204);
            api().get("/api/sessions").then().statusCode(200)
                    .body("find { it.id == '" + a.id() + "' }.pending.assumptions", equalTo(1))
                    .body("find { it.id == '" + a.id() + "' }.pending.openQuestions", equalTo(0));

            Map<String, Object> body = Map.of("participants", List.of("Julie M."), "specs", List.of(
                    Map.of("sessionId", a.id().toString(), "excluded", List.of()),
                    Map.of("sessionId", b.id().toString(), "excluded", List.of("H-01"))));
            String id = api().body(body).post("/api/interviews").then().statusCode(201)
                    .body("state", equalTo("PREPARED")).body("specs", hasSize(2))
                    .body("specs[1].excluded", equalTo(List.of("H-01"))).extract().path("id");
            api().get("/api/interviews?state=PREPARED").then().statusCode(200).body("id", hasItem(id));

            api().body(Map.of("participants", List.of("Paul D."), "specs", List.of(
                    Map.of("sessionId", b.id().toString(), "excluded", List.of("H-09")))))
                    .put("/api/interviews/" + id).then().statusCode(422);
            api().body(Map.of("participants", List.of("Paul D."), "specs", List.of(
                    Map.of("sessionId", b.id().toString()), Map.of("sessionId", a.id().toString()))))
                    .put("/api/interviews/" + id).then().statusCode(200).body("specs[0].sessionId", equalTo(b.id().toString()))
                    .body("participants", equalTo(List.of("Paul D.")));

            api().body(Map.of()).post("/api/interviews/" + id + "/start").then().statusCode(200)
                    .body("state", equalTo("IN_PROGRESS")).body("specs.version", equalTo(List.of(1, 1)));
            api().delete("/api/interviews/" + id).then().statusCode(409).body("code", equalTo("INTERVIEW_NOT_PREPARED"));
            api().body(Map.of()).post("/api/sessions/" + a.id() + "/interviews").then().statusCode(409)
                    .body("code", equalTo("INTERVIEW_BUSY"));
        } finally {
            api().body(Map.of("projectId", previous.toString())).put("/api/projects/current");
        }
    }

    @Test
    void uneRelectureSansRienNoterNEstPasGardee() {
        Session s = specification();
        String id = api().body(Map.of()).post("/api/sessions/" + s.id() + "/interviews").then().statusCode(201)
                .extract().path("id");
        api().post("/api/interviews/" + id + "/finish").then().statusCode(204);
        api().get("/api/interviews/" + id).then().statusCode(404);
        api().get("/api/sessions/" + s.id() + "/interviews").then().statusCode(200).body("$", hasSize(0));
    }

    @Test
    void validation() {
        Session s = specification();
        String id = api().body(Map.of()).post("/api/sessions/" + s.id() + "/interviews").then().statusCode(201)
                .extract().path("id");
        Map<String, Object> unknownStep = point(s.id(), "REMARK", null, "x", "CHANGE");
        unknownStep.put("anchor", Map.of("element", "UC-01", "step", 9, "alternative", false));
        api().body(unknownStep).post("/api/interviews/" + id + "/points").then().statusCode(422);
        api().body(point(s.id(), "BOGUS", null, "x", "CHANGE")).post("/api/interviews/" + id + "/points")
                .then().statusCode(422);
        api().body(point(s.id(), "REMARK", null, "x".repeat(5_001), "CHANGE")).post("/api/interviews/" + id + "/points")
                .then().statusCode(422);
        api().get("/api/interviews/" + UUID.randomUUID()).then().statusCode(404);
        api().body(Map.of()).post("/api/sessions/" + UUID.randomUUID() + "/interviews").then().statusCode(404);
    }

    @Test
    void sansSessionRefuse() {
        given().get("/api/interviews/" + UUID.randomUUID()).then().statusCode(403);
        given().contentType("application/json").body("{}").post("/api/sessions/" + UUID.randomUUID() + "/interviews")
                .then().statusCode(403);
    }
}
