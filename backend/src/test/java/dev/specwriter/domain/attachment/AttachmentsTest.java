package dev.specwriter.domain.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 009 : règles des documents joints (data-model.md). */
class AttachmentsTest {

    static final Instant T = Instant.parse("2026-11-09T10:00:00Z");

    static Attachment attachment(int ordinal, String fileName, String text) {
        return Attachment.create(UUID.randomUUID(), UUID.randomUUID(), ordinal, null, fileName, text, T);
    }

    @Test
    void nomDuFichierSansCheminEtFormat() {
        assertThat(Attachments.fileName("C:\\Users\\moi\\Téléchargements\\CR COPIL.pdf")).isEqualTo("CR COPIL.pdf");
        assertThat(Attachments.fileName("/Users/moi/Downloads/Suivi.XLSX")).isEqualTo("Suivi.XLSX");
        assertThat(AttachmentFormat.of("Suivi.XLSX")).contains(AttachmentFormat.XLSX);
        assertThat(AttachmentFormat.of("page.htm")).contains(AttachmentFormat.HTML);
        assertThat(AttachmentFormat.of("ancien.doc")).isEmpty();
        assertThat(AttachmentFormat.of("sans-extension")).isEmpty();
        assertThatThrownBy(() -> Attachments.fileName("  ")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> Attachments.fileName("x".repeat(252) + ".pdf"))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> Attachments.format("ancien.doc")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.UNSUPPORTED_FORMAT));
    }

    @Test
    void titreEmpreinteEtReference() {
        Attachment a = Attachment.create(UUID.randomUUID(), UUID.randomUUID(), 1, " ", "CR COPIL.pdf", "Texte", T);
        assertThat(a.title()).isEqualTo("CR COPIL");
        assertThat(a.ref()).isEqualTo("joint:CR COPIL.pdf");
        assertThat(a.chars()).isEqualTo(5);
        assertThat(a.format()).isEqualTo(AttachmentFormat.PDF);
        assertThat(a.contentHash()).hasSize(64).isEqualTo(Attachments.hash("Texte"));
        assertThat(Attachment.create(UUID.randomUUID(), UUID.randomUUID(), 1, "Compte rendu", "CR.pdf", "x", T).title())
                .isEqualTo("Compte rendu");
        assertThat(a.view()).isEqualTo(new Attachment.View(a.id(), "CR COPIL", "CR COPIL.pdf", AttachmentFormat.PDF, 5, T));
        assertThat(a.toString()).doesNotContain("Texte");
    }

    @Test
    void plafondEtDoublons() {
        List<Attachment> five = List.of(attachment(1, "a.pdf", "a"), attachment(2, "b.pdf", "b"),
                attachment(3, "c.pdf", "c"), attachment(4, "d.pdf", "d"), attachment(5, "e.pdf", "e"));
        assertThatThrownBy(() -> Attachments.requireRoom(five, "f.pdf", Attachments.hash("f")))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ATTACHMENT_LIMIT));
        List<Attachment> two = five.subList(0, 2);
        assertThatThrownBy(() -> Attachments.requireRoom(two, "z.pdf", Attachments.hash("a")))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ATTACHMENT_DUPLICATE));
        assertThatThrownBy(() -> Attachments.requireRoom(two, "A.PDF", Attachments.hash("z")))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.ATTACHMENT_DUPLICATE));
        Attachments.requireRoom(two, "c.pdf", Attachments.hash("c"));
        assertThat(Attachments.nextOrdinal(two)).isEqualTo(3);
        assertThat(Attachments.nextOrdinal(List.of())).isEqualTo(1);
    }

    @Test
    void refusDeLectureExpliqueSansTexte() {
        assertThat(Attachments.unreadable(UnreadReason.PROTECTED).code()).isEqualTo(ErrorCode.DOCUMENT_UNREADABLE);
        assertThat(Attachments.unreadable(UnreadReason.PROTECTED).userMessage()).contains("mot de passe");
        assertThat(Attachments.unreadable(UnreadReason.NO_TEXT).userMessage()).contains("texte");
        assertThat(Attachments.unreadable(UnreadReason.TOO_LARGE).userMessage()).contains("20 Mo");
        assertThat(Attachments.unreadable(UnreadReason.UNAVAILABLE).userMessage()).contains("trop longtemps");
        assertThat(Attachments.unreadable(UnreadReason.UNREADABLE).userMessage()).contains("illisible");
    }
}
