package dev.specwriter.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.conversation.SpecificationDiscussion;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Implementation;
import dev.specwriter.domain.session.ImplementationStatus;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import dev.specwriter.support.InMemoryConversations;
import dev.specwriter.support.InMemorySpecificationDiscussions;
import dev.specwriter.support.JiraWorld;
import java.time.Instant;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 011, US1 et US3 : ouvrir, reprendre et recommencer la discussion d'une spécification. */
class DiscussSpecificationTest {

    final JiraWorld w = new JiraWorld();
    final InMemoryConversations conversations = new InMemoryConversations();
    final InMemorySpecificationDiscussions discussions = new InMemorySpecificationDiscussions();
    DiscussSpecification discuss;
    Session session;

    @BeforeEach
    void setUp() {
        discuss = new DiscussSpecification(w.sessions, w.versions, conversations, discussions, w.clock, w.ids);
        session = w.generatedSession();
    }

    static void refused(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void ouvreUneDiscussionPuisLaReprend() {
        assertThat(discuss.current(session.id())).isEmpty();

        DiscussSpecification.View opened = discuss.open(session.id());

        assertThat(opened.version()).isEqualTo(1);
        assertThat(opened.specificationName()).isEqualTo(session.name());
        assertThat(conversations.find(opened.conversationId())).hasValueSatisfying(c -> {
            assertThat(c.projectId()).isEqualTo(Project.DEFAULT_ID);
            assertThat(c.name()).startsWith("Discussion : ");
        });
        assertThat(discuss.open(session.id()).conversationId()).isEqualTo(opened.conversationId());
        assertThat(discuss.current(session.id())).contains(opened);
        assertThat(discuss.of(opened.conversationId())).hasValueSatisfying(c -> {
            assertThat(c.sessionId()).isEqualTo(session.id());
            assertThat(c.version()).isEqualTo(1);
        });
    }

    @Test
    void nouvelleDiscussionGardeLAncienneDansLaListe() {
        DiscussSpecification.View first = discuss.open(session.id());

        DiscussSpecification.View fresh = discuss.fresh(session.id());

        assertThat(fresh.conversationId()).isNotEqualTo(first.conversationId());
        assertThat(discuss.current(session.id())).map(DiscussSpecification.View::conversationId)
                .contains(fresh.conversationId());
        assertThat(discussions.ofConversation(first.conversationId())).map(SpecificationDiscussion::current)
                .contains(false);
        assertThat(conversations.find(first.conversationId())).isPresent();
    }

    @Test
    void refuseUneSpecificationVerrouilleeSansVersionOuInconnue() {
        w.sessions.saveImplementation(session.id(), new Implementation(ImplementationStatus.IMPLEMENTED,
                Instant.parse("2026-11-10T09:00:00Z"), null));
        refused(() -> discuss.open(session.id()), ErrorCode.SPECIFICATION_LOCKED);
        refused(() -> discuss.fresh(session.id()), ErrorCode.SPECIFICATION_LOCKED);

        Session empty = Session.start(w.ids.next(), Project.DEFAULT_ID, Need.of(JiraWorld.NEED), null, w.clock.now());
        w.sessions.save(empty, Questionnaire.empty());
        refused(() -> discuss.open(empty.id()), ErrorCode.VALIDATION_FAILED);

        refused(() -> discuss.open(UUID.randomUUID()), ErrorCode.NOT_FOUND);
    }

    @Test
    void contexteDUneConversationOrdinaireOuDetachee() {
        DiscussSpecification.View opened = discuss.open(session.id());
        assertThat(discuss.of(UUID.randomUUID())).isEmpty();

        discussions.detachAll(session.id());

        assertThat(discuss.of(opened.conversationId())).isEmpty();
    }
}
