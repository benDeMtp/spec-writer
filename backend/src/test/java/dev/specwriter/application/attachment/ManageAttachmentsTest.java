package dev.specwriter.application.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.session.ChangeStatus;
import dev.specwriter.domain.attachment.Attachment;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.AttachmentReader;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.domain.session.ImplementationStatus;
import dev.specwriter.domain.session.Session;
import dev.specwriter.support.InMemoryAttachments;
import dev.specwriter.support.JiraWorld;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 009, US3 : joindre ou retirer un document d'une session en cours (contracts/api.md). */
class ManageAttachmentsTest {

    final JiraWorld w = new JiraWorld();
    final InMemoryAttachments attachments = new InMemoryAttachments();
    /** Lecteur simulé : le contenu est le texte ; « PROTEGE » simule un document protégé. */
    final AttachmentReader reader = (name, content) -> {
        String text = new String(content, StandardCharsets.UTF_8);
        return "PROTEGE".equals(text) ? AttachmentReader.Reading.failed(UnreadReason.PROTECTED)
                : AttachmentReader.Reading.of(null, text);
    };
    ManageAttachments manage;
    Session session;

    @BeforeEach
    void setUp() {
        manage = new ManageAttachments(w.sessions, attachments, reader, w.clock, w.ids);
        session = w.generatedSession();
    }

    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static void refused(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void ajoutDansLOrdreEtRetrait() {
        Attachment.View cr = manage.add(session.id(), "CR COPIL.pdf", bytes("Le délai est de 14 jours."));
        manage.add(session.id(), "Annexe.docx", bytes("Annexe"));

        assertThat(manage.list(session.id())).extracting(Attachment.View::fileName)
                .containsExactly("CR COPIL.pdf", "Annexe.docx");
        assertThat(cr.title()).isEqualTo("CR COPIL");
        assertThat(attachments.list(session.id()).getFirst().text()).isEqualTo("Le délai est de 14 jours.");

        manage.remove(session.id(), cr.id());
        assertThat(manage.list(session.id())).extracting(Attachment.View::fileName).containsExactly("Annexe.docx");
        // le suivant prend le rang d'après, jamais un rang déjà donné
        manage.add(session.id(), "Autre.pdf", bytes("Autre"));
        assertThat(attachments.list(session.id())).extracting(Attachment::ordinal).containsExactly(2, 3);
        refused(() -> manage.remove(session.id(), cr.id()), ErrorCode.NOT_FOUND);
    }

    @Test
    void refusPlafondDoublonsFormatEtLecture() {
        for (int i = 1; i <= 5; i++) {
            manage.add(session.id(), "D" + i + ".pdf", bytes("Texte " + i));
        }
        refused(() -> manage.add(session.id(), "D6.pdf", bytes("Texte 6")), ErrorCode.ATTACHMENT_LIMIT);
        manage.remove(session.id(), manage.list(session.id()).getLast().id());
        refused(() -> manage.add(session.id(), "Copie.pdf", bytes("Texte 1")), ErrorCode.ATTACHMENT_DUPLICATE);
        refused(() -> manage.add(session.id(), "d1.PDF", bytes("Autre texte")), ErrorCode.ATTACHMENT_DUPLICATE);
        refused(() -> manage.add(session.id(), "ancien.doc", bytes("x")), ErrorCode.UNSUPPORTED_FORMAT);
        refused(() -> manage.add(session.id(), "Secret.pdf", bytes("PROTEGE")), ErrorCode.DOCUMENT_UNREADABLE);
        assertThat(manage.list(session.id())).hasSize(4);
        refused(() -> manage.list(UUID.randomUUID()), ErrorCode.NOT_FOUND);
    }

    @Test
    void featureImplementeeVerrouillee() {
        Attachment.View cr = manage.add(session.id(), "CR COPIL.pdf", bytes("Le délai est de 14 jours."));
        int versions = w.versions.list(session.id()).size();
        ChangeStatus status = new ChangeStatus(w.sessions, w.clock);
        status.run(session.id(), ImplementationStatus.READY, false);
        status.run(session.id(), ImplementationStatus.IN_PROGRESS, false);
        status.run(session.id(), ImplementationStatus.IMPLEMENTED, false);

        refused(() -> manage.add(session.id(), "Annexe.pdf", bytes("Annexe")), ErrorCode.SPECIFICATION_LOCKED);
        refused(() -> manage.remove(session.id(), cr.id()), ErrorCode.SPECIFICATION_LOCKED);
        assertThat(manage.list(session.id())).hasSize(1);
        assertThat(w.versions.list(session.id())).hasSize(versions);
    }
}
