package dev.specwriter.application.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.attachment.AttachmentDraft;
import dev.specwriter.domain.attachment.AttachmentFormat;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.AttachmentReader;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.support.FixedClock;
import dev.specwriter.support.SequentialIdGenerator;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 009 : documents joints avant le démarrage, en mémoire seulement (research.md R3). */
class AttachmentDraftsTest {

    final FixedClock clock = new FixedClock();
    /** Lecteur simulé : le contenu est le texte ; « PROTEGE » ou « VIDE » simulent un refus. */
    final AttachmentReader reader = (name, content) -> {
        String text = new String(content, StandardCharsets.UTF_8);
        return switch (text) {
            case "PROTEGE" -> AttachmentReader.Reading.failed(UnreadReason.PROTECTED);
            case "VIDE" -> AttachmentReader.Reading.of("Vide", "  ");
            default -> AttachmentReader.Reading.of(text.startsWith("#") ? text.substring(1, 6) : null, text);
        };
    };
    final AttachmentDrafts drafts = new AttachmentDrafts(reader, clock, new SequentialIdGenerator());

    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void lectureEtBrouillon() {
        AttachmentDraft d = drafts.create("/tmp/CR COPIL.pdf", bytes("Le délai est de 14 jours."));
        assertThat(d.fileName()).isEqualTo("CR COPIL.pdf");
        assertThat(d.format()).isEqualTo(AttachmentFormat.PDF);
        assertThat(d.title()).isEqualTo("CR COPIL");
        assertThat(drafts.get(List.of(d.id()))).containsExactly(d);
        drafts.forget(List.of(d.id()));
        assertThat(drafts.find(List.of(d.id()))).isEmpty();
    }

    @Test
    void refusAvecLaRaison() {
        assertThatThrownBy(() -> drafts.create("ancien.doc", bytes("x"))).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.UNSUPPORTED_FORMAT));
        assertThatThrownBy(() -> drafts.create("Secret.pdf", bytes("PROTEGE"))).isInstanceOfSatisfying(DomainException.class,
                e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.DOCUMENT_UNREADABLE);
                    assertThat(e.userMessage()).contains("mot de passe");
                });
        assertThatThrownBy(() -> drafts.create("Scan.pdf", bytes("VIDE"))).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.userMessage()).contains("pas de texte"));
        assertThatThrownBy(() -> drafts.create(" ", bytes("x"))).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void expirationEtPlafondEnMemoire() {
        AttachmentDraft old = drafts.create("a.pdf", bytes("A"));
        clock.advance(Duration.ofMinutes(61));
        AttachmentDraft fresh = drafts.create("b.pdf", bytes("B"));
        assertThat(drafts.find(List.of(old.id(), fresh.id()))).containsExactly(fresh);
        assertThatThrownBy(() -> drafts.get(List.of(old.id()))).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));

        for (int i = 0; i < AttachmentDrafts.MAX_DRAFTS; i++) {
            drafts.create("n" + i + ".pdf", bytes("N" + i));
        }
        assertThat(drafts.find(List.of(fresh.id()))).isEmpty();
    }

    @Test
    void pourLeDemarrageCinqAuPlusSansDoublon() {
        List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ids.add(drafts.create("d" + i + ".pdf", bytes("D" + i)).id());
        }
        assertThat(drafts.get(ids.subList(0, 5))).hasSize(5);
        assertThatThrownBy(() -> drafts.get(ids)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.ATTACHMENT_LIMIT));
        UUID same = drafts.create("copie.pdf", bytes("D0")).id();
        assertThatThrownBy(() -> drafts.get(List.of(ids.get(0), same))).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.ATTACHMENT_DUPLICATE));
        assertThat(drafts.get(List.of())).isEmpty();
        assertThat(drafts.get(null)).isEmpty();
    }
}
