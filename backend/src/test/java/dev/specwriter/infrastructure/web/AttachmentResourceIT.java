package dev.specwriter.infrastructure.web;

import static dev.specwriter.support.LocalApi.api;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import dev.specwriter.support.TestDocuments;
import io.quarkus.test.junit.QuarkusTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 009 : envoi d'un document joint (contracts/api.md), réponse sans le texte. */
@QuarkusTest
class AttachmentResourceIT {

    @TempDir
    Path dir;

    @jakarta.inject.Inject
    jakarta.persistence.EntityManager em;

    byte[] pdf(String name, String text) throws Exception {
        Path file = dir.resolve(name);
        TestDocuments.pdf(file, "Compte rendu", List.of(text));
        return Files.readAllBytes(file);
    }

    @Test
    void brouillonCreeEtOublie() throws Exception {
        String id = api().contentType("application/octet-stream").queryParam("name", "CR COPIL.pdf")
                .body(pdf("cr.pdf", "Le délai de rétractation est de 14 jours."))
                .post("/api/attachment-drafts").then().statusCode(201)
                .body("id", notNullValue()).body("title", equalTo("Compte rendu"))
                .body("fileName", equalTo("CR COPIL.pdf")).body("format", equalTo("PDF"))
                .body(not(containsString("rétractation"))).extract().path("id");
        api().delete("/api/attachment-drafts/" + id).then().statusCode(204);
    }

    @Test
    void refusExpliques() throws Exception {
        api().contentType("application/octet-stream").queryParam("name", "ancien.doc").body(new byte[] {1, 2, 3})
                .post("/api/attachment-drafts").then().statusCode(422).body("code", equalTo("UNSUPPORTED_FORMAT"));
        Path secret = dir.resolve("secret.pdf");
        TestDocuments.encryptedPdf(secret);
        api().contentType("application/octet-stream").queryParam("name", "secret.pdf").body(Files.readAllBytes(secret))
                .post("/api/attachment-drafts").then().statusCode(422).body("code", equalTo("DOCUMENT_UNREADABLE"))
                .body("userMessage", containsString("mot de passe"));
        // le contenu brut n'est accepté que sur les routes d'envoi
        api().contentType("application/octet-stream").body(new byte[] {1}).post("/api/sessions").then().statusCode(415);
    }

    @Test
    void documentAuDelaDe256KoAccepte() throws Exception {
        StringBuilder text = new StringBuilder();
        while (text.length() < 3_000) {
            text.append("Le délai est de 14 jours. ");
        }
        byte[] big = new byte[300 * 1024];
        byte[] content = pdf("gros.pdf", text.toString());
        System.arraycopy(content, 0, big, 0, content.length);
        // un PDF suivi de zéros reste lisible par PDFBox ; il dépasse la limite des autres routes
        api().contentType("application/octet-stream").queryParam("name", "gros.pdf").body(big)
                .post("/api/attachment-drafts").then().statusCode(201);
    }

    @Test
    void etatDuBrouillonAnnonceCommeUneMention() throws Exception {
        String id = api().contentType("application/octet-stream").queryParam("name", "CR COPIL.pdf")
                .body(pdf("etat.pdf", "Le délai de rétractation est de 14 jours."))
                .post("/api/attachment-drafts").then().statusCode(201).extract().path("id");
        api().body(java.util.Map.of("fields", List.of(), "attachmentIds", List.of(id)))
                .post("/api/projects/" + dev.specwriter.domain.project.Project.DEFAULT_ID + "/mentions/resolve")
                .then().statusCode(200).body("[0].field", equalTo("ATTACHMENT")).body("[0].state", equalTo("TRANSMITTED"))
                .body("[0].document.kind", equalTo("ATTACHMENT")).body("[0].document.path", equalTo("CR COPIL.pdf"))
                .body(not(containsString("rétractation")));
        api().body(java.util.Map.of("fields", List.of(), "attachmentIds", List.of("pas-un-id")))
                .post("/api/projects/" + dev.specwriter.domain.project.Project.DEFAULT_ID + "/mentions/resolve")
                .then().statusCode(422);
        api().delete("/api/attachment-drafts/" + id).then().statusCode(204);
    }

    String draft(String name, String text) throws Exception {
        return api().contentType("application/octet-stream").queryParam("name", name).body(pdf(name, text))
                .post("/api/attachment-drafts").then().statusCode(201).extract().path("id");
    }

    String upload(String sessionId, String name, String text, int status) throws Exception {
        return api().contentType("application/octet-stream").queryParam("name", name).body(pdf(name, text))
                .post("/api/sessions/" + sessionId + "/attachments").then().statusCode(status).extract().path("id");
    }

    /** US1 à US3 : joint au démarrage, liste sans texte, ajout, refus, retrait, suppression de la session (SC-003). */
    @Test
    void documentsDeLaSessionPuisSuppressionSansTrace() throws Exception {
        api().body(java.util.Map.of("granted", true)).put("/api/consents/COPILOT").then().statusCode(204);
        String draftId = draft("CR COPIL.pdf", "Le délai de rétractation confidentiel est de 14 jours.");
        String id = api().body(java.util.Map.of("need", SessionResourceIT.NEED + " IT-OK", "attachmentIds",
                List.of(draftId))).post("/api/sessions").then().statusCode(201).extract().path("id");
        // le brouillon est rattaché puis oublié
        api().body(java.util.Map.of("need", SessionResourceIT.NEED + " IT-OK", "attachmentIds", List.of(draftId)))
                .post("/api/sessions").then().statusCode(422).body("code", equalTo("VALIDATION_FAILED"));

        api().get("/api/sessions/" + id + "/attachments").then().statusCode(200)
                .body("fileName", org.hamcrest.Matchers.contains("CR COPIL.pdf"))
                .body("[0].chars", org.hamcrest.Matchers.greaterThan(0))
                .body(not(containsString("rétractation")));

        String annexe = upload(id, "Annexe.pdf", "Annexe sur les avoirs.", 201);
        api().contentType("application/octet-stream").queryParam("name", "cr copil.PDF")
                .body(pdf("autre.pdf", "Un autre texte.")).post("/api/sessions/" + id + "/attachments").then()
                .statusCode(422).body("code", equalTo("ATTACHMENT_DUPLICATE"));
        api().delete("/api/sessions/" + id + "/attachments/" + annexe).then().statusCode(204);
        api().delete("/api/sessions/" + id + "/attachments/" + annexe).then().statusCode(404);
        api().get("/api/sessions/" + id + "/attachments").then().statusCode(200)
                .body("fileName", org.hamcrest.Matchers.contains("CR COPIL.pdf"));

        api().delete("/api/sessions/" + id).then().statusCode(204);
        api().get("/api/sessions/" + id + "/attachments").then().statusCode(404);
        Number left = (Number) em.createNativeQuery("select count(*) from session_attachment where session_id = ?1")
                .setParameter(1, id).getSingleResult();
        org.assertj.core.api.Assertions.assertThat(left.intValue()).isZero();
    }
}
