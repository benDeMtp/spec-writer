package dev.specwriter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.attachment.Attachment;
import dev.specwriter.domain.port.AttachmentRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 009 : documents joints rangés avec la session, uniques, supprimés avec elle (research.md R4). */
@QuarkusTest
class AttachmentPersistenceIT {

    static final Instant T0 = Instant.parse("2026-11-09T09:00:00Z");

    @Inject
    AttachmentRepository attachments;

    @Inject
    SessionRepository sessions;

    @Inject
    EntityManager em;

    Session session() {
        Session s = Session.start(UUID.randomUUID(), Project.DEFAULT_ID,
                Need.of("Le gestionnaire doit pouvoir annuler une facture émise par un avoir total."), null, T0);
        sessions.save(s, Questionnaire.empty());
        return s;
    }

    @Test
    void relusDansLOrdreSansTexteDansLesVues() {
        Session s = session();
        Attachment b = Attachment.create(UUID.randomUUID(), s.id(), 2, null, "B.pdf", "Texte B", T0.plusSeconds(1));
        Attachment a = Attachment.create(UUID.randomUUID(), s.id(), 1, "Compte rendu", "A.docx", "Texte A", T0);
        attachments.add(b);
        attachments.add(a);

        assertThat(attachments.list(s.id())).containsExactly(a, b);
        assertThat(attachments.views(s.id())).containsExactly(a.view(), b.view());
        assertThat(attachments.list(UUID.randomUUID())).isEmpty();

        assertThat(attachments.remove(s.id(), a.id())).isTrue();
        assertThat(attachments.remove(s.id(), a.id())).isFalse();
        assertThat(attachments.list(s.id())).containsExactly(b);
    }

    @Test
    void doublonsRefusesParLaBase() {
        Session s = session();
        attachments.add(Attachment.create(UUID.randomUUID(), s.id(), 1, null, "A.pdf", "même texte", T0));
        assertThatThrownBy(() -> attachments.add(Attachment.create(UUID.randomUUID(), s.id(), 2, null, "Autre.pdf",
                "même texte", T0))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void supprimesAvecLaSession() {
        Session s = session();
        attachments.add(Attachment.create(UUID.randomUUID(), s.id(), 1, null, "A.pdf", "Texte confidentiel", T0));
        sessions.delete(s.id());
        assertThat(attachments.list(s.id())).isEmpty();
        Number left = (Number) em.createNativeQuery("select count(*) from session_attachment where session_id = ?1")
                .setParameter(1, s.id().toString()).getSingleResult();
        assertThat(left.intValue()).isZero();
    }
}
