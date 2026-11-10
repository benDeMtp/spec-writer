package dev.specwriter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.SpecificationDiscussion;
import dev.specwriter.domain.port.ConversationRepository;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationDiscussionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectName;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.Specs;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.VersionOrigin;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 011 : discussions des spécifications et versions qui en sont issues (V22). */
@QuarkusTest
class SpecificationDiscussionPersistenceIT {

    static final Instant T0 = Instant.parse("2026-11-11T09:00:00Z");

    @Inject
    SpecificationDiscussionRepository discussions;

    @Inject
    ConversationRepository conversations;

    @Inject
    SpecificationRepository versions;

    @Inject
    ProjectRepository projects;

    @Inject
    SessionRepository sessions;

    final List<UUID> createdProjects = new ArrayList<>();
    final List<UUID> createdSessions = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdSessions.forEach(id -> sessions.find(id).ifPresent(s -> sessions.delete(id)));
        createdProjects.forEach(id -> projects.find(id).ifPresent(p -> projects.delete(id)));
    }

    Project project() {
        Project p = Project.create(UUID.randomUUID(), ProjectName.of("Discussions " + UUID.randomUUID()), null, null, T0);
        projects.save(p);
        createdProjects.add(p.id());
        return p;
    }

    Session session(Project p) {
        Session s = Session.start(UUID.randomUUID(), p.id(),
                Need.of("Le gestionnaire doit pouvoir réserver une salle pour une réunion d'équipe."), null, T0);
        sessions.save(s, Questionnaire.empty());
        createdSessions.add(s.id());
        return s;
    }

    Conversation conversation(Project p) {
        Conversation c = Conversation.start(UUID.randomUUID(), p.id(), "Que dit la spécification ?", T0);
        conversations.save(c);
        return c;
    }

    @Test
    void uneSeuleDiscussionSuivieParSpecificationEtRenommage() {
        Project p = project();
        Session s = session(p);
        Conversation first = conversation(p);
        Conversation second = conversation(p);
        discussions.save(SpecificationDiscussion.start(first.id(), s.id(), "Réserver une salle", T0));
        assertThat(discussions.current(s.id())).map(SpecificationDiscussion::conversationId).contains(first.id());

        // une seconde discussion suivie est refusée tant que la première l'est encore
        assertThatThrownBy(() -> discussions.save(SpecificationDiscussion.start(second.id(), s.id(), "Réserver", T0)))
                .isInstanceOf(RuntimeException.class);
        discussions.save(discussions.ofConversation(first.id()).orElseThrow().retired());
        discussions.save(SpecificationDiscussion.start(second.id(), s.id(), "Réserver une salle", T0.plusSeconds(1)));

        assertThat(discussions.current(s.id())).map(SpecificationDiscussion::conversationId).contains(second.id());
        assertThat(discussions.bySession(s.id())).extracting(SpecificationDiscussion::conversationId)
                .containsExactly(first.id(), second.id());
        assertThat(discussions.ofConversations(Set.of(first.id(), UUID.randomUUID()))).containsOnlyKeys(first.id());

        discussions.renameAll(s.id(), "Réserver une salle de réunion");
        assertThat(discussions.bySession(s.id())).extracting(SpecificationDiscussion::specificationName)
                .containsOnly("Réserver une salle de réunion");
    }

    @Test
    void detachementSuppressionEnCascadeEtVersionDuTour() {
        Project p = project();
        Session s = session(p);
        Conversation kept = conversation(p);
        discussions.save(SpecificationDiscussion.start(kept.id(), s.id(), "Réserver une salle", T0));
        ConversationMessage answer = ConversationMessage.agentStarted(UUID.randomUUID(), kept.id(), 1, T0).about(3);
        conversations.saveMessage(answer);
        assertThat(conversations.messages(kept.id()).getFirst().specificationVersion()).isEqualTo(3);

        discussions.detachAll(s.id());
        SpecificationDiscussion detached = discussions.ofConversation(kept.id()).orElseThrow();
        assertThat(detached.attached()).isFalse();
        assertThat(detached.current()).isFalse();
        assertThat(detached.specificationName()).isEqualTo("Réserver une salle");

        // la suppression de la conversation emporte son rattachement
        conversations.delete(kept.id());
        assertThat(discussions.ofConversation(kept.id())).isEmpty();
    }

    @Test
    void suppressionDeLaSpecificationGardeLaDiscussionSansRattachement() {
        Project p = project();
        Session s = session(p);
        Conversation c = conversation(p);
        discussions.save(SpecificationDiscussion.start(c.id(), s.id(), "Réserver une salle", T0));

        sessions.delete(s.id());

        assertThat(discussions.ofConversation(c.id())).hasValueSatisfying(d -> assertThat(d.attached()).isFalse());
    }

    @Test
    void versionIssueDeLaDiscussionLueAvecSonOrigineEtSaConversation() {
        Project p = project();
        Session s = session(p);
        Conversation c = conversation(p);
        SpecificationVersion v = new SpecificationVersion(UUID.randomUUID(), s.id(), 1, Specs.valid(), "proposal:x",
                VersionOrigin.DISCUSSION, "# Paiement", T0);
        versions.save(v);
        assertThat(versions.find(s.id(), 1)).map(SpecificationVersion::origin).contains(VersionOrigin.DISCUSSION);
        assertThat(versions.discussionOf(v.id())).isEmpty();

        versions.linkDiscussion(v.id(), c.id());
        assertThat(versions.discussionOf(v.id())).contains(c.id());

        // la conversation supprimée, la version reste « issue de la discussion », sans lien
        conversations.delete(c.id());
        assertThat(versions.find(s.id(), 1)).map(SpecificationVersion::origin).contains(VersionOrigin.DISCUSSION);
        assertThat(versions.discussionOf(v.id())).isEmpty();
        SpecificationVersion edited = new SpecificationVersion(UUID.randomUUID(), s.id(), 2, Specs.valid(), "proposal:y",
                VersionOrigin.AI_EDIT, "# Paiement", T0);
        versions.save(edited);
        assertThat(versions.find(s.id(), 2)).map(SpecificationVersion::origin).contains(VersionOrigin.AI_EDIT);
    }
}
