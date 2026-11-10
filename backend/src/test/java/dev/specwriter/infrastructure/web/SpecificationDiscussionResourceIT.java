package dev.specwriter.infrastructure.web;

import static dev.specwriter.support.LocalApi.api;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Implementation;
import dev.specwriter.domain.session.ImplementationStatus;
import dev.specwriter.domain.session.Language;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.MarkdownRenderer;
import dev.specwriter.domain.specification.Specs;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.VersionOrigin;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 011, US1 : discussion d'une spécification, contexte transmis et cité par section, verrou. */
@QuarkusTest
class SpecificationDiscussionResourceIT {

    static final Instant T0 = Instant.parse("2026-11-11T09:00:00Z");

    @Inject
    SessionRepository sessions;

    @Inject
    SpecificationRepository versions;

    Session session;

    @BeforeEach
    void setUp() {
        ConversationResourceIT.consent(true);
        session = Session.start(UUID.randomUUID(), Project.DEFAULT_ID,
                Need.of("Le client doit pouvoir payer sa commande en ligne par carte."), "Paiement en ligne", T0);
        sessions.save(session, Questionnaire.empty());
        versions.save(new SpecificationVersion(UUID.randomUUID(), session.id(), 1, Specs.valid(), "fp",
                VersionOrigin.GENERATED, MarkdownRenderer.render(Specs.valid(), Language.FR), T0));
    }

    @AfterEach
    void tearDown() {
        ConversationResourceIT.consent(false);
        sessions.find(session.id()).ifPresent(s -> sessions.delete(s.id()));
    }

    String base() {
        return "/api/sessions/" + session.id() + "/discussion";
    }

    @Test
    void ouvreDiscuteCiteParSectionEtRecommence() {
        api().get(base()).then().statusCode(204);
        JsonPath opened = api().body(Map.of()).post(base()).then().statusCode(201)
                .body("specificationName", equalTo("Paiement en ligne")).body("version", equalTo(1)).extract().jsonPath();
        String cid = opened.getString("conversationId");
        api().get(base()).then().statusCode(200).body("conversationId", equalTo(cid));
        api().body(Map.of()).post(base()).then().statusCode(201).body("conversationId", equalTo(cid));

        String turn = api().body(Map.of("text", "IT-DISCUSSION Que dit la spécification du paiement ?"))
                .post("/api/conversations/" + cid + "/messages").then().statusCode(202).extract().path("turnId");
        String stream = api().accept("text/event-stream").get("/api/conversations/" + cid + "/turns/" + turn + "/events")
                .then().statusCode(200).extract().asString();
        assertThat(stream).contains("event:done").contains("\"state\":\"COMPLETE\"");

        JsonPath detail = api().get("/api/conversations/" + cid).then().statusCode(200).extract().jsonPath();
        assertThat(detail.getString("specification.name")).isEqualTo("Paiement en ligne");
        assertThat(detail.getString("specification.sessionId")).isEqualTo(session.id().toString());
        assertThat(detail.getInt("messages[1].specificationVersion")).isEqualTo(1);
        assertThat(detail.getList("messages[1].sources.url", String.class)).containsExactly(
                "/sessions/" + session.id() + "/versions/1#introduction",
                "/sessions/" + session.id() + "/versions/1#UC-01");
        JsonPath list = api().get("/api/projects/" + Project.DEFAULT_ID + "/conversations").then().statusCode(200)
                .extract().jsonPath();
        assertThat(list.getString("find { it.id == '" + cid + "' }.specification.name")).isEqualTo("Paiement en ligne");

        String fresh = api().body(Map.of("fresh", true)).post(base()).then().statusCode(201).extract()
                .path("conversationId");
        assertThat(fresh).isNotEqualTo(cid);
        api().get(base()).then().statusCode(200).body("conversationId", equalTo(fresh));
        api().get("/api/conversations/" + cid).then().statusCode(200).body("specification.name",
                equalTo("Paiement en ligne"));
        api().delete("/api/conversations/" + cid).then().statusCode(204);
        api().delete("/api/conversations/" + fresh).then().statusCode(204);
    }

    @Test
    void specificationImplementeeNeSeDiscutePas() {
        String cid = api().body(Map.of()).post(base()).then().statusCode(201).extract().path("conversationId");
        sessions.saveImplementation(session.id(), new Implementation(ImplementationStatus.IMPLEMENTED, T0, null));

        api().body(Map.of()).post(base()).then().statusCode(409).body("code", equalTo("SPECIFICATION_LOCKED"));
        api().body(Map.of("text", "IT-DISCUSSION Et maintenant ?")).post("/api/conversations/" + cid + "/messages")
                .then().statusCode(409).body("code", equalTo("SPECIFICATION_LOCKED"));
        api().delete("/api/conversations/" + cid).then().statusCode(204);
        api().post("/api/sessions/" + UUID.randomUUID() + "/discussion").then().statusCode(404);
    }

    /** 011, US3 : le renommage suit, la suppression emporte ou garde les discussions. */
    @Test
    void renommageEtSuppressionAvecOuSansLesDiscussions() {
        String cid = api().body(Map.of()).post(base()).then().statusCode(201).extract().path("conversationId");
        api().get("/api/sessions/" + session.id() + "/discussions/count").then().statusCode(200)
                .body("count", equalTo(1));
        api().body(Map.of("name", "Paiement par carte")).patch("/api/sessions/" + session.id()).then().statusCode(200);
        api().get("/api/conversations/" + cid).then().statusCode(200)
                .body("specification.name", equalTo("Paiement par carte"));

        api().delete("/api/sessions/" + session.id() + "?discussions=autre").then().statusCode(422);
        api().delete("/api/sessions/" + session.id() + "?discussions=keep").then().statusCode(204);
        api().get("/api/conversations/" + cid).then().statusCode(200)
                .body("specification.name", equalTo("Paiement par carte"))
                .body("specification.sessionId", org.hamcrest.Matchers.nullValue());
        api().delete("/api/conversations/" + cid).then().statusCode(204);

        // sans option, la suppression emporte les discussions
        setUp();
        String other = api().body(Map.of()).post(base()).then().statusCode(201).extract().path("conversationId");
        api().delete("/api/sessions/" + session.id()).then().statusCode(204);
        api().get("/api/conversations/" + other).then().statusCode(404);
    }
}
