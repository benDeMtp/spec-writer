package dev.specwriter.infrastructure.web;

import static dev.specwriter.support.LocalApi.api;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import dev.specwriter.domain.project.Project;
import io.quarkus.test.junit.QuarkusTest;
import dev.specwriter.support.TestDocuments;
import io.restassured.path.json.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 010 : conversation, tour en flux SSE rejoué, refus (contracts/api.md), IA rejouée. */
@QuarkusTest
class ConversationResourceIT {

    static final String BASE = "/api/projects/" + Project.DEFAULT_ID + "/conversations";

    @BeforeEach
    void consent() {
        consent(true);
    }

    @AfterEach
    void withdraw() {
        consent(false);
    }

    static void consent(boolean granted) {
        api().body(Map.of("granted", granted)).put("/api/consents/COPILOT").then().statusCode(204);
    }

    static String create() {
        return api().post(BASE).then().statusCode(201).body("name", equalTo("Nouvelle conversation")).extract().path("id");
    }

    @Test
    void questionEnFluxPuisConversationRelue() {
        String id = create();
        JsonPath started = api().body(Map.of("text", "IT-AGENT Comment sont émis les avoirs ?"))
                .post("/api/conversations/" + id + "/messages").then().statusCode(202).extract().jsonPath();
        String turn = started.getString("turnId");

        String stream = api().accept("text/event-stream").get("/api/conversations/" + id + "/turns/" + turn + "/events")
                .then().statusCode(200).extract().asString();
        assertThat(stream).contains("event:step").contains("event:delta").contains("event:done")
                .contains("\"state\":\"COMPLETE\"");

        JsonPath detail = api().get("/api/conversations/" + id).then().statusCode(200).extract().jsonPath();
        assertThat(detail.getString("name")).isEqualTo("IT-AGENT Comment sont émis les avoirs ?");
        assertThat(detail.getList("messages.role")).containsExactly("USER", "AGENT");
        assertThat(detail.getString("messages[1].state")).isEqualTo("COMPLETE");
        // la citation [9] ne désigne aucune source du tour : elle est retirée
        assertThat(detail.getString("messages[1].text")).startsWith("Je ne trouve pas").doesNotContain("[9]");
        assertThat(detail.getList("messages[1].steps.query")).containsExactly("avoirs émission");
        assertThat(api().get(BASE).then().statusCode(200).extract().jsonPath().getList("id")).contains(id);

        api().body(Map.of("name", "Avoirs")).patch("/api/conversations/" + id).then().statusCode(200)
                .body("name", equalTo("Avoirs"));
        api().delete("/api/conversations/" + id).then().statusCode(204);
        api().get("/api/conversations/" + id).then().statusCode(404).body("code", equalTo("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void refus() {
        String id = create();
        api().body(Map.of("text", "x".repeat(8_001))).post("/api/conversations/" + id + "/messages").then()
                .statusCode(422).body("code", equalTo("MESSAGE_TOO_LONG"));
        api().body(Map.of("text", " ")).post("/api/conversations/" + id + "/messages").then().statusCode(422);
        api().delete("/api/conversations/" + id + "/turns/" + UUID.randomUUID()).then().statusCode(404);
        api().get("/api/conversations/pas-un-id").then().statusCode(404);
        api().post("/api/projects/" + UUID.randomUUID() + "/conversations").then().statusCode(404);
        // exemples tirés de la connaissance du projet : une liste, vide tant que rien n'est indexé
        api().get(BASE + "/suggestions").then().statusCode(200).body("size()", lessThanOrEqualTo(3));
        consent(false);
        api().body(Map.of("text", "IT-AGENT Bonjour ?")).post("/api/conversations/" + id + "/messages").then()
                .statusCode(428);
        // flux d'un tour inconnu : l'écran est invité à relire la conversation
        assertThat(api().accept("text/event-stream").get("/api/conversations/" + id + "/turns/" + UUID.randomUUID()
                + "/events").then().statusCode(200).extract().asString()).contains("event:gone");
    }

    @Test
    void brouillonRelPuisSpecificationLiee() {
        String id = create();
        String turn = api().body(Map.of("text", "IT-AGENT Rédigeons la spécification de l'annulation d'un avoir."))
                .post("/api/conversations/" + id + "/messages").then().statusCode(202).extract().path("turnId");
        api().accept("text/event-stream").get("/api/conversations/" + id + "/turns/" + turn + "/events").then()
                .statusCode(200);

        JsonPath draft = api().post("/api/conversations/" + id + "/need-draft").then().statusCode(200).extract().jsonPath();
        assertThat(draft.getString("need")).contains("annuler un avoir");
        // FACT-404 n'a pas été cité dans la conversation : le point est écarté
        assertThat(draft.getList("points.question")).containsExactly("Qui annule un avoir ?");

        Map<String, Object> corrected = Map.of("theme", "ACTORS", "question", "Qui annule un avoir ?", "answer",
                "Le gestionnaire, ou son responsable.", "basis", "USER", "sourceRef", "");
        JsonPath session = api().body(Map.of("need", draft.getString("need"), "points", List.of(corrected)))
                .post("/api/conversations/" + id + "/specifications").then().statusCode(201).extract().jsonPath();
        String sessionId = session.getString("id");
        assertThat(session.getList("answeredQuestions.answer")).containsExactly("Le gestionnaire, ou son responsable.");
        assertThat(session.getList("answeredQuestions.rationale")).containsExactly("Établi dans la conversation");
        assertThat(session.getList("currentBatch.theme")).doesNotContain("ACTORS");

        api().get("/api/sessions/" + sessionId + "/conversation").then().statusCode(200).body("id", equalTo(id));
        assertThat(api().get("/api/conversations/" + id).then().extract().jsonPath().getList("sessions.sessionId"))
                .containsExactly(sessionId);

        api().body(Map.of("need", "trop court", "points", List.of())).post("/api/conversations/" + id + "/specifications")
                .then().statusCode(422);
        api().body(Map.of("need", draft.getString("need"), "points", List.of(Map.of("theme", "INCONNU", "question", "q",
                "answer", "a")))).post("/api/conversations/" + id + "/specifications").then().statusCode(422)
                .body("code", equalTo("NEED_DRAFT_INVALID"));

        // supprimer la conversation garde la spécification, qui n'a plus d'origine
        api().delete("/api/conversations/" + id).then().statusCode(204);
        api().get("/api/sessions/" + sessionId).then().statusCode(200);
        api().get("/api/sessions/" + sessionId + "/conversation").then().statusCode(204);
        api().delete("/api/sessions/" + sessionId).then().statusCode(204);
    }

    @Test
    void documentJointLuParLAgentEtRepris(@TempDir Path dir) throws Exception {
        String id = create();
        Path file = dir.resolve("lettrage.pdf");
        TestDocuments.pdf(file, "Règles de lettrage", List.of("Le lettrage se fait sous 48 heures."));
        String aid = api().contentType("application/octet-stream").queryParam("name", "lettrage.pdf")
                .body(Files.readAllBytes(file)).post("/api/conversations/" + id + "/attachments").then().statusCode(201)
                .body("title", equalTo("Règles de lettrage")).extract().path("id");
        assertThat(api().get("/api/conversations/" + id + "/attachments").then().statusCode(200).extract().asString())
                .contains("lettrage.pdf").doesNotContain("48 heures");
        api().body(Map.of("fields", List.of(), "conversationId", id))
                .post("/api/projects/" + Project.DEFAULT_ID + "/mentions/resolve").then().statusCode(200)
                .body("[0].field", equalTo("ATTACHMENT")).body("[0].document.path", equalTo("lettrage.pdf"));

        String turn = api().body(Map.of("text", "IT-INCOHERENCE Le délai de lettrage est-il de 72 heures ?"))
                .post("/api/conversations/" + id + "/messages").then().statusCode(202).extract().path("turnId");
        api().accept("text/event-stream").get("/api/conversations/" + id + "/turns/" + turn + "/events").then()
                .statusCode(200);
        // le document joint est la source 1 ; la source 2 n'existe pas et son appel est retiré
        JsonPath answer = api().get("/api/conversations/" + id).then().extract().jsonPath();
        assertThat(answer.getString("messages[1].text")).contains("48 heures [1]").doesNotContain("[2]");
        assertThat(answer.getList("messages[1].sources.kind")).containsExactly("ATTACHMENT");
        String sessionId = api().body(Map.of("need", "IT-OK Permettre au gestionnaire de lettrer un avoir sous 48 heures.",
                "points", List.of())).post("/api/conversations/" + id + "/specifications").then().statusCode(201)
                .extract().path("id");
        api().get("/api/sessions/" + sessionId + "/attachments").then().statusCode(200)
                .body("fileName", equalTo(List.of("lettrage.pdf")));

        api().delete("/api/conversations/" + id + "/attachments/" + aid).then().statusCode(204);
        api().get("/api/conversations/" + id + "/attachments").then().body("size()", equalTo(0));
        api().delete("/api/sessions/" + sessionId).then().statusCode(204);
        api().delete("/api/conversations/" + id).then().statusCode(204);
    }

    /**
     * Mémoire : des échanges qui débordent sont résumés pendant le tour, ce que suit le flux. La part
     * de l'historique dépend du modèle choisi : les échanges s'enchaînent jusqu'au premier résumé.
     */
    @Test
    void echangesAnciensResumesPendantLeTour() {
        String id = create();
        String stream = "";
        int turns = 0;
        while (!stream.contains("event:memory") && turns < 12) {
            String turn = api().body(Map.of("text", "IT-COMPACT " + turns + " " + "besoin ".repeat(1_100)))
                    .post("/api/conversations/" + id + "/messages").then().statusCode(202).extract().path("turnId");
            stream = api().accept("text/event-stream").get("/api/conversations/" + id + "/turns/" + turn + "/events")
                    .then().statusCode(200).extract().asString();
            turns++;
        }
        assertThat(stream).contains("\"phase\":\"STARTED\"").contains("\"phase\":\"DONE\"");

        JsonPath detail = api().get("/api/conversations/" + id).then().statusCode(200).extract().jsonPath();
        assertThat(detail.getInt("memoryUntil")).isPositive();
        assertThat(detail.getList("messages.ordinal", Integer.class)).hasSize(2 * turns).startsWith(1, 2);
        assertThat(detail.getString("messages[" + (2 * turns - 1) + "].text")).isEqualTo("Réponse brève.");
        assertThat(detail.getBoolean("messages[" + (2 * turns - 1) + "].historyTruncated")).isFalse();
        api().delete("/api/conversations/" + id).then().statusCode(204);
    }
}
