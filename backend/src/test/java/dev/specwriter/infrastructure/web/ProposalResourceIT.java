package dev.specwriter.infrastructure.web;

import static dev.specwriter.support.LocalApi.api;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.question.Questionnaire;
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

/** 011, US2 : nouvelle version proposée d'après la discussion, relue, appliquée ou abandonnée. */
@QuarkusTest
class ProposalResourceIT {

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

    String discuss(boolean fresh, String text) {
        String cid = api().body(Map.of("fresh", fresh)).post("/api/sessions/" + session.id() + "/discussion").then()
                .statusCode(201).extract().path("conversationId");
        String turn = api().body(Map.of("text", text)).post("/api/conversations/" + cid + "/messages").then()
                .statusCode(202).extract().path("turnId");
        api().accept("text/event-stream").get("/api/conversations/" + cid + "/turns/" + turn + "/events").then()
                .statusCode(200);
        return cid;
    }

    String stream(String cid) {
        String turn = api().post("/api/conversations/" + cid + "/proposals").then().statusCode(202).extract()
                .path("turnId");
        return api().accept("text/event-stream").get("/api/conversations/" + cid + "/turns/" + turn + "/events").then()
                .statusCode(200).extract().asString();
    }

    static String proposalId(String stream) {
        var m = java.util.regex.Pattern.compile("\"proposalId\":\"([^\"]+)\"").matcher(stream);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    void proposeRelitAppliqueEtLaVersionEstIssueDeLaDiscussion() {
        String cid = discuss(false, "IT-DISCUSSION IT-REVISION Le client annule tant que la commande n'est pas envoyée.");

        String stream = stream(cid);

        assertThat(stream).contains("event:started").contains("\"stage\":\"REVISE\"").contains("event:reasoning")
                .contains("event:done");
        String pid = proposalId(stream);
        String base = "/api/sessions/" + session.id();
        JsonPath proposal = api().get(base + "/proposals/" + pid).then().statusCode(200)
                .body("baseVersion", equalTo(1)).body("proposed.origin", equalTo("DISCUSSION"))
                .body("proposed.conversationId", equalTo(cid)).extract().jsonPath();
        String ref = proposal.getString("proposed.useCases[1].source.ref");
        assertThat(ref).isEqualTo("discussion:" + cid + "#1");
        assertThat(proposal.getString("proposed.links['" + ref + "']")).isEqualTo("/conversations/" + cid);
        assertThat(proposal.getString("proposed.markdown")).contains("Discussion du ").contains(", message 1");

        api().post(base + "/ai-edits/" + pid + "/accept").then().statusCode(201).body("number", equalTo(2))
                .body("origin", equalTo("DISCUSSION")).body("conversationId", equalTo(cid));
        api().get(base + "/proposals/" + pid).then().statusCode(404);
        api().get(base + "/versions").then().statusCode(200).body("[0].origin", equalTo("GENERATED"))
                .body("[0].conversationId", nullValue()).body("[1].origin", equalTo("DISCUSSION"))
                .body("[1].conversationId", equalTo(cid));
        api().get(base + "/versions/2").then().statusCode(200).body("conversationId", equalTo(cid));
        api().get(base + "/discussion").then().statusCode(200).body("version", equalTo(2));

        // la conversation supprimée, la version reste « Issue de la discussion », sans lien
        api().delete("/api/conversations/" + cid).then().statusCode(204);
        api().get(base + "/versions/2").then().statusCode(200).body("origin", equalTo("DISCUSSION"))
                .body("conversationId", nullValue());
    }

    @Test
    void aucunChangementAbandonEtRefus() {
        String cid = discuss(false, "IT-DISCUSSION IT-SANS-CHANGEMENT Que dit l'UC-01 ?");

        String stream = stream(cid);

        assertThat(stream).contains("\"unchanged\":true").contains("La discussion n'a posé que des questions.");
        String base = "/api/sessions/" + session.id();
        api().delete(base + "/proposals/" + UUID.randomUUID()).then().statusCode(204);
        api().get(base + "/proposals/" + UUID.randomUUID()).then().statusCode(404);

        String ordinary = api().post("/api/projects/" + Project.DEFAULT_ID + "/conversations").then().statusCode(201)
                .extract().path("id");
        api().post("/api/conversations/" + ordinary + "/proposals").then().statusCode(422)
                .body("code", equalTo("NOT_A_DISCUSSION"));
        String empty = api().body(Map.of("fresh", true)).post(base + "/discussion").then().statusCode(201).extract()
                .path("conversationId");
        api().post("/api/conversations/" + empty + "/proposals").then().statusCode(422)
                .body("code", equalTo("VALIDATION_FAILED"));
        api().delete("/api/conversations/" + ordinary).then().statusCode(204);
        api().delete("/api/conversations/" + empty).then().statusCode(204);
        api().delete("/api/conversations/" + cid).then().statusCode(204);
    }
}
