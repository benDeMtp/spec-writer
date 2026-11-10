package dev.specwriter.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.attachment.Attachment;
import dev.specwriter.domain.attachment.AttachmentDraft;
import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.AttachmentReader;
import dev.specwriter.domain.project.Project;
import dev.specwriter.support.InMemoryConversationAttachments;
import dev.specwriter.support.InMemoryConversations;
import dev.specwriter.support.JiraWorld;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 010, US3 : documents joints d'une conversation, règles de 009. */
class ConversationAttachmentsTest {

    final JiraWorld w = new JiraWorld();
    final InMemoryConversations conversations = new InMemoryConversations();
    final InMemoryConversationAttachments attachments = new InMemoryConversationAttachments();
    /** Lecteur simulé : le contenu est le texte. */
    final AttachmentReader reader = (name, content) -> AttachmentReader.Reading.of(null,
            new String(content, StandardCharsets.UTF_8));
    ConversationAttachments manage;
    Conversation conversation;

    @BeforeEach
    void setUp() {
        manage = new ConversationAttachments(conversations, attachments, reader, w.clock, w.ids);
        conversation = new Conversation(UUID.randomUUID(), Project.DEFAULT_ID, "Lettrage", Instant.EPOCH, Instant.EPOCH);
        conversations.save(conversation);
    }

    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static Consumer<Throwable> code(ErrorCode code) {
        return e -> assertThat(((DomainException) e).code()).isEqualTo(code);
    }

    @Test
    void ajouteListeCopieEtRetire() {
        Attachment.View added = manage.add(conversation.id(), "regles.html", bytes("Le lettrage se fait sous 48 heures."));
        manage.add(conversation.id(), "cr.pdf", bytes("Compte rendu."));

        assertThat(manage.list(conversation.id())).extracting(Attachment.View::fileName)
                .containsExactly("regles.html", "cr.pdf");
        // une spécification reçoit des copies : identifiants nouveaux, même texte, même ordre
        assertThat(manage.copies(conversation.id())).extracting(AttachmentDraft::fileName, AttachmentDraft::text)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("regles.html", "Le lettrage se fait sous 48 heures."),
                        org.assertj.core.groups.Tuple.tuple("cr.pdf", "Compte rendu."));
        assertThat(manage.copies(conversation.id()).getFirst().id()).isNotEqualTo(added.id());

        manage.remove(conversation.id(), added.id());
        assertThat(manage.list(conversation.id())).hasSize(1);
        assertThatThrownBy(() -> manage.remove(conversation.id(), added.id())).satisfies(code(ErrorCode.NOT_FOUND));
    }

    @Test
    void refusComme009() {
        assertThatThrownBy(() -> manage.list(UUID.randomUUID())).satisfies(code(ErrorCode.CONVERSATION_NOT_FOUND));
        assertThatThrownBy(() -> manage.add(conversation.id(), "ancien.doc", bytes("x")))
                .satisfies(code(ErrorCode.UNSUPPORTED_FORMAT));
        manage.add(conversation.id(), "a.pdf", bytes("Un."));
        assertThatThrownBy(() -> manage.add(conversation.id(), "b.pdf", bytes("Un.")))
                .isInstanceOf(DomainException.class);
        for (int i = 0; i < 4; i++) {
            manage.add(conversation.id(), "d" + i + ".pdf", bytes("Texte " + i));
        }
        assertThatThrownBy(() -> manage.add(conversation.id(), "f.pdf", bytes("Encore.")))
                .satisfies(code(ErrorCode.ATTACHMENT_LIMIT));
    }
}
