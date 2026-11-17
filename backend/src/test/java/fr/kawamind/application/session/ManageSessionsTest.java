package fr.kawamind.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Need;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.specification.SpecificationVersion;
import fr.kawamind.domain.specification.Specs;
import fr.kawamind.domain.specification.VersionOrigin;
import fr.kawamind.support.FixedClock;
import fr.kawamind.support.InMemoryProjects;
import fr.kawamind.support.InMemorySessions;
import fr.kawamind.support.InMemorySpecifications;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Lister, renommer, supprimer (FR-017). */
class ManageSessionsTest {

    final InMemorySessions sessions = new InMemorySessions();
    final InMemorySpecifications versions = new InMemorySpecifications();
    final FixedClock clock = new FixedClock();
    final ManageSessions manage = new ManageSessions(sessions, versions, new InMemoryProjects(), clock);

    Session add(String name) {
        Session s = Session.start(UUID.randomUUID(), Project.DEFAULT_ID, Need.of(SessionUseCasesTest.NEED), name,
                clock.now());
        sessions.save(s, Questionnaire.empty());
        clock.advance(Duration.ofMinutes(1));
        return s;
    }

    @Test
    void listeTrieeParDateDeModificationDecroissante() {
        Session a = add("A");
        Session b = add("B");
        versions.save(new SpecificationVersion(UUID.randomUUID(), a.id(), 1, Specs.valid(), "fp", VersionOrigin.GENERATED,
                "md", clock.now()));
        assertThat(manage.list()).extracting(e -> e.session().name()).containsExactly("B", "A");
        manage.rename(a.id(), "A renommée");
        assertThat(manage.list()).extracting(e -> e.session().name()).containsExactly("A renommée", "B");
        assertThat(manage.list().getFirst().latestVersion()).isEqualTo(1);
        assertThat(manage.list().get(1).latestVersion()).isZero();
        assertThat(b.name()).isEqualTo("B");
    }

    @Test
    void renommageDeUnACentVingtCaracteres() {
        Session s = add("Avant");
        assertThat(manage.rename(s.id(), "  Après  ").name()).isEqualTo("Après");
        assertThatThrownBy(() -> manage.rename(s.id(), " ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> manage.rename(s.id(), "x".repeat(121))).isInstanceOf(DomainException.class);
        assertThat(manage.rename(s.id(), "x".repeat(120)).name()).hasSize(120);
        assertThatThrownBy(() -> manage.rename(UUID.randomUUID(), "x"))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void suppressionPhysiqueAvecLesVersions() {
        Session s = add("A");
        Session other = add("B");
        versions.save(new SpecificationVersion(UUID.randomUUID(), s.id(), 1, Specs.valid(), "fp", VersionOrigin.GENERATED,
                "md", clock.now()));
        assertThat(manage.versions(s.id())).hasSize(1);
        manage.delete(s.id());
        assertThat(sessions.find(s.id())).isEmpty();
        assertThat(versions.list(s.id())).isEmpty();
        assertThat(manage.list()).extracting(e -> e.session().id()).containsExactly(other.id());
        assertThatThrownBy(() -> manage.delete(s.id())).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> manage.versions(s.id())).isInstanceOf(DomainException.class);
    }

    /** 011, US3 : les discussions suivent le renommage, et la suppression les emporte ou les garde. */
    final fr.kawamind.support.InMemoryConversations conversations = new fr.kawamind.support.InMemoryConversations();
    final fr.kawamind.support.InMemorySpecificationDiscussions discussions =
            new fr.kawamind.support.InMemorySpecificationDiscussions();
    final fr.kawamind.application.specification.OperationRegistry operations =
            new fr.kawamind.application.specification.OperationRegistry();
    final ManageSessions withDiscussions = new ManageSessions(sessions, versions, new InMemoryProjects(), clock, null,
            operations, conversations, discussions);

    UUID discuss(Session s, boolean current) {
        UUID cid = UUID.randomUUID();
        conversations.save(new fr.kawamind.domain.conversation.Conversation(cid, Project.DEFAULT_ID,
                "Discussion : " + s.name(), clock.now(), clock.now()));
        fr.kawamind.domain.conversation.SpecificationDiscussion d =
                fr.kawamind.domain.conversation.SpecificationDiscussion.start(cid, s.id(), s.name(), clock.now());
        discussions.save(current ? d : d.retired());
        return cid;
    }

    @Test
    void leRenommageSuitDansLesDiscussions() {
        Session s = add("Avoirs");
        UUID cid = discuss(s, true);

        withDiscussions.rename(s.id(), "Avoirs partiels");

        assertThat(discussions.ofConversation(cid)).hasValueSatisfying(
                d -> assertThat(d.specificationName()).isEqualTo("Avoirs partiels"));
    }

    @Test
    void suppressionAvecSesDiscussionsOuEnLesGardant() {
        Session a = add("A");
        UUID current = discuss(a, true);
        UUID older = discuss(a, false);
        Session b = add("B");
        UUID kept = discuss(b, true);
        assertThat(withDiscussions.discussions(a.id())).isEqualTo(2);

        withDiscussions.delete(a.id(), false);

        assertThat(conversations.find(current)).isEmpty();
        assertThat(conversations.find(older)).isEmpty();
        assertThat(sessions.find(a.id())).isEmpty();

        withDiscussions.delete(b.id(), true);

        assertThat(conversations.find(kept)).isPresent();
        assertThat(discussions.ofConversation(kept)).hasValueSatisfying(d -> {
            assertThat(d.attached()).isFalse();
            assertThat(d.specificationName()).isEqualTo("B");
        });
    }

    @Test
    void suppressionRefuseePendantUneReponseDansUneDiscussion() {
        Session s = add("A");
        UUID cid = discuss(s, true);

        try (var answering = operations.begin(cid)) {
            assertThatThrownBy(() -> withDiscussions.delete(s.id(), true)).isInstanceOfSatisfying(
                    DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.OPERATION_IN_PROGRESS));
        }
        withDiscussions.delete(s.id(), true);
        assertThat(sessions.find(s.id())).isEmpty();
    }
}
