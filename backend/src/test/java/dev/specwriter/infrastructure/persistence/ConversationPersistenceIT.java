package dev.specwriter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.conversation.CitedSource;
import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.MessageState;
import dev.specwriter.domain.conversation.SearchStep;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.port.ConversationRepository;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectName;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 010 : conversations rangées par projet, supprimées avec lui (research.md R6). */
@QuarkusTest
class ConversationPersistenceIT {

    static final Instant T0 = Instant.parse("2026-11-09T09:00:00Z");

    @Inject
    ConversationRepository conversations;

    @Inject
    ProjectRepository projects;

    @Inject
    SessionRepository sessions;

    @Inject
    EntityManager em;

    /** Projets créés par le test : supprimés après, sinon l'un d'eux deviendrait le projet courant par défaut. */
    final List<UUID> created = new ArrayList<>();

    Project project() {
        Project p = Project.create(UUID.randomUUID(), ProjectName.of("Conversations " + UUID.randomUUID()), null, null, T0);
        projects.save(p);
        created.add(p.id());
        return p;
    }

    @AfterEach
    void cleanUp() {
        created.forEach(id -> projects.find(id).ifPresent(p -> projects.delete(id)));
    }

    Conversation conversation(Project p, Instant at) {
        Conversation c = Conversation.start(UUID.randomUUID(), p.id(), "Comment sont émis les avoirs ?", at);
        conversations.save(c);
        return c;
    }

    @Test
    void messagesEtapesSourcesDansLOrdre() {
        Project p = project();
        Conversation older = conversation(p, T0);
        Conversation c = conversation(p, T0.plusSeconds(60));
        assertThat(conversations.list(p.id())).extracting(Conversation::id).containsExactly(c.id(), older.id());

        ConversationMessage question = ConversationMessage.user(UUID.randomUUID(), c.id(), 1, "Comment sont émis les avoirs ?", T0);
        ConversationMessage answer = ConversationMessage.agentStarted(UUID.randomUUID(), c.id(), 2, T0);
        conversations.saveMessage(question);
        conversations.saveMessage(answer);
        conversations.saveStep(answer.id(), new SearchStep(1, "émission des avoirs", Set.of(SourceKind.TICKET, SourceKind.ADR), 6));
        conversations.saveStep(answer.id(), new SearchStep(2, "avoir-émis", Set.of(SourceKind.ARCH_ELEMENT), 2));
        conversations.saveMessage(answer.withText("Par le bus [1].").finished(MessageState.COMPLETE, true));
        conversations.saveSources(answer.id(), List.of(new CitedSource(1, SourceKind.ADR, "adr/0005.html", "5. Bus")));

        assertThat(conversations.messages(c.id())).extracting(ConversationMessage::text)
                .containsExactly("Comment sont émis les avoirs ?", "Par le bus [1].");
        assertThat(conversations.messages(c.id()).get(1)).satisfies(m -> {
            assertThat(m.state()).isEqualTo(MessageState.COMPLETE);
            assertThat(m.historyTruncated()).isTrue();
        });
        assertThat(conversations.steps(answer.id())).extracting(SearchStep::query)
                .containsExactly("émission des avoirs", "avoir-émis");
        assertThat(conversations.steps(answer.id()).getFirst().kinds()).containsExactlyInAnyOrder(SourceKind.TICKET, SourceKind.ADR);
        assertThat(conversations.sources(answer.id())).singleElement()
                .isEqualTo(new CitedSource(1, SourceKind.ADR, "adr/0005.html", "5. Bus"));

        conversations.save(c.renamed("Avoirs").touched(T0.plusSeconds(120)));
        assertThat(conversations.find(c.id()).orElseThrow().name()).isEqualTo("Avoirs");
    }

    @Test
    void reponseEnCoursInterrompueAuDemarrage() {
        Conversation c = conversation(project(), T0);
        ConversationMessage running = ConversationMessage.agentStarted(UUID.randomUUID(), c.id(), 1, T0);
        conversations.saveMessage(running.withText("Début"));
        assertThat(conversations.interruptRunning()).isPositive();
        assertThat(conversations.messages(c.id()).getFirst()).satisfies(m -> {
            assertThat(m.state()).isEqualTo(MessageState.INCOMPLETE);
            assertThat(m.text()).isEqualTo("Début");
        });
    }

    @Test
    void suppressionsEnCascade() {
        Project p = project();
        Conversation c = conversation(p, T0);
        ConversationMessage m = ConversationMessage.user(UUID.randomUUID(), c.id(), 1, "Texte confidentiel du message", T0);
        conversations.saveMessage(m);
        Session s = Session.start(UUID.randomUUID(), p.id(),
                Need.of("Le gestionnaire doit pouvoir annuler une facture émise par un avoir total."), null, T0);
        sessions.save(s, Questionnaire.empty());
        conversations.link(c.id(), s.id());
        assertThat(conversations.sessions(c.id())).containsExactly(s.id());
        assertThat(conversations.conversationOf(s.id())).contains(c.id());

        // la spécification démarrée depuis la conversation reste
        conversations.delete(c.id());
        assertThat(conversations.find(c.id())).isEmpty();
        assertThat(sessions.find(s.id())).isPresent();
        assertThat(count("conversation_message where conversation_id = ?1", c.id())).isZero();
        assertThat(conversations.conversationOf(s.id())).isEmpty();

        // le projet emporte ses conversations
        Conversation other = conversation(p, T0);
        conversations.saveMessage(ConversationMessage.user(UUID.randomUUID(), other.id(), 1, "Encore", T0));
        sessions.delete(s.id());
        projects.delete(p.id());
        assertThat(conversations.find(other.id())).isEmpty();
        assertThat(count("conversation_message where conversation_id = ?1", other.id())).isZero();
    }

    int count(String where, UUID id) {
        return ((Number) em.createNativeQuery("select count(*) from " + where).setParameter(1, id.toString())
                .getSingleResult()).intValue();
    }
}
