package dev.specwriter.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.Specs;
import dev.specwriter.domain.specification.VersionOrigin;
import dev.specwriter.support.FixedClock;
import dev.specwriter.support.InMemorySessions;
import dev.specwriter.support.InMemorySpecifications;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Lister, renommer, supprimer (FR-017). */
class ManageSessionsTest {

    final InMemorySessions sessions = new InMemorySessions();
    final InMemorySpecifications versions = new InMemorySpecifications();
    final FixedClock clock = new FixedClock();
    final ManageSessions manage = new ManageSessions(sessions, versions, clock);

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
}
