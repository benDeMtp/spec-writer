package dev.specwriter.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.specification.OperationRegistry;
import dev.specwriter.domain.conversation.CitedSource;
import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.MessageState;
import dev.specwriter.domain.conversation.SearchStep;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.session.Session;
import dev.specwriter.support.InMemoryConversations;
import dev.specwriter.support.JiraWorld;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 010, US1 et US4 : liste, détail, renommage, suppression. */
class ManageConversationsTest {

    static final Instant T = Instant.parse("2026-11-09T09:00:00Z");

    final JiraWorld w = new JiraWorld();
    final InMemoryConversations conversations = new InMemoryConversations();
    final OperationRegistry operations = new OperationRegistry();
    final ManageConversations manage = new ManageConversations(w.projects, conversations, w.sessions, w.jira, operations);

    Conversation conversation(Instant at) {
        Conversation c = Conversation.start(UUID.randomUUID(), Project.DEFAULT_ID, "Comment sont émis les avoirs ?", at);
        conversations.save(c);
        return c;
    }

    @Test
    void listeEtDetailAvecAdressesDesSources() {
        Conversation older = conversation(T);
        Conversation c = conversation(T.plusSeconds(60));
        assertThat(manage.list(Project.DEFAULT_ID)).extracting(Conversation::id).containsExactly(c.id(), older.id());

        conversations.saveMessage(ConversationMessage.user(UUID.randomUUID(), c.id(), 1, "Comment ?", T));
        ConversationMessage answer = ConversationMessage.agentStarted(UUID.randomUUID(), c.id(), 2, T)
                .withText("Voir [1], [2] et [3].").finished(MessageState.COMPLETE, false);
        conversations.saveMessage(answer);
        conversations.saveStep(answer.id(), new SearchStep(1, "avoirs", Set.of(), 3));
        Session session = w.generatedSession();
        conversations.saveSources(answer.id(), List.of(
                new CitedSource(1, SourceKind.SPECIFICATION, "spec:" + session.id() + "@v2", "Saisir un avoir"),
                new CitedSource(2, SourceKind.ADR, "https://docs/adr/0005.html", "5. Bus"),
                new CitedSource(3, SourceKind.ARCH_ELEMENT, "Facturation/Avoirs", "Avoirs")));
        conversations.link(c.id(), session.id());

        ManageConversations.Detail d = manage.detail(c.id());
        assertThat(d.messages()).hasSize(2);
        assertThat(d.messages().getFirst().steps()).isEmpty();
        assertThat(d.messages().get(1).steps()).hasSize(1);
        assertThat(d.messages().get(1).sources()).extracting(ManageConversations.SourceView::url)
                .containsExactly("/sessions/" + session.id() + "/versions/2", "https://docs/adr/0005.html", null);
        assertThat(d.sessions()).singleElement().satisfies(l -> assertThat(l.sessionId()).isEqualTo(session.id()));
        assertThat(manage.origin(session.id())).map(Conversation::id).contains(c.id());
        assertThat(ManageConversations.specificationUrl("FACT-7")).isNull();
    }

    @Test
    void renommerEtSupprimer() {
        Conversation c = conversation(T);
        assertThat(manage.rename(c.id(), "Avoirs").name()).isEqualTo("Avoirs");
        try (OperationRegistry.Operation op = operations.begin(c.id())) {
            assertThatThrownBy(() -> manage.delete(c.id())).isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.code()).isEqualTo(ErrorCode.OPERATION_IN_PROGRESS));
        }
        manage.delete(c.id());
        assertThatThrownBy(() -> manage.detail(c.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
        assertThatThrownBy(() -> manage.list(UUID.randomUUID())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }
}
