package dev.specwriter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.llm.Contradiction;
import dev.specwriter.domain.llm.ProposedQuestion;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.question.QuestionState;
import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.MarkdownRenderer;
import dev.specwriter.domain.specification.SpecificationContent;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.Specs;
import dev.specwriter.domain.specification.VersionOrigin;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SessionPersistenceIT {

    static final Instant T0 = Instant.parse("2026-10-30T09:00:00Z");

    @Inject
    SessionRepository sessions;

    @Inject
    SpecificationRepository versions;

    Session session() {
        return Session.start(UUID.randomUUID(), Project.DEFAULT_ID,
                Need.of("Les employés doivent pouvoir réserver une salle de réunion pour une durée donnée."), null, T0);
    }

    Questionnaire questionnaire() {
        return Questionnaire.empty().addBatch(List.of(
                        new ProposedQuestion(QuestionTheme.GOAL, "Quel objectif ?", "Pour cadrer", List.of("A", "B"), "A", List.of()),
                        new ProposedQuestion(QuestionTheme.ERRORS, "Et si la salle est prise ?", "Cas d'erreur", List.of(), null,
                                List.of())),
                List.of(new Contradiction("Gratuit ou payant", "Le service est-il payant ?")), Set.of(QuestionTheme.ACTORS),
                UUID::randomUUID);
    }

    @Test
    void sessionEtQuestionnaireRelusALIdentique() {
        Session s = session();
        Questionnaire q = questionnaire();
        UUID first = q.active().get(1).id();
        UUID second = q.active().get(2).id();
        q = q.answer(first, "Éviter les doublons", T0).skip(second, T0.plusSeconds(1));
        sessions.save(s, q);

        assertThat(sessions.find(s.id())).contains(s);
        Questionnaire reloaded = sessions.questionnaire(s.id()).orElseThrow();
        assertThat(reloaded).isEqualTo(q);
        assertThat(reloaded.coveredByNeed()).containsExactly(QuestionTheme.ACTORS);
        assertThat(reloaded.question(second).orElseThrow().answer().assumption()).isTrue();

        // une réponse retirée est supprimée, une question devenue obsolète est conservée
        sessions.save(s.answerChanged(T0.plusSeconds(5)), reloaded.markObsolete(Set.of(first)));
        Questionnaire after = sessions.questionnaire(s.id()).orElseThrow();
        assertThat(after.question(first).orElseThrow().state()).isEqualTo(QuestionState.OBSOLETE);
        assertThat(after.question(first).orElseThrow().answer()).isNull();
        assertThat(sessions.list()).extracting(dev.specwriter.domain.session.SessionListing::id).contains(s.id());
    }

    @Test
    void versionsParNumeroEtEmpreinte() {
        Session s = session();
        sessions.save(s, Questionnaire.empty());
        SpecificationContent content = Specs.valid();
        assertThat(versions.lastNumber(s.id())).isZero();
        SpecificationVersion v1 = new SpecificationVersion(UUID.randomUUID(), s.id(), 1, content, "fp-1",
                VersionOrigin.GENERATED, MarkdownRenderer.render(content, s.language()), T0.truncatedTo(ChronoUnit.MILLIS));
        versions.save(v1);
        versions.save(new SpecificationVersion(UUID.randomUUID(), s.id(), 2, content, "fp-2", VersionOrigin.GENERATED,
                v1.markdown(), T0));

        assertThat(versions.find(s.id(), 1)).contains(v1);
        assertThat(versions.findByFingerprint(s.id(), "fp-2").orElseThrow().number()).isEqualTo(2);
        assertThat(versions.findByFingerprint(s.id(), "autre")).isEmpty();
        assertThat(versions.list(s.id())).extracting(SpecificationVersion::number).containsExactly(1, 2);
        assertThat(versions.lastNumber(s.id())).isEqualTo(2);
        // numéro unique par session
        assertThatThrownBy(() -> versions.save(new SpecificationVersion(UUID.randomUUID(), s.id(), 2, content, "x",
                VersionOrigin.GENERATED, "x", T0))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void suppressionEnCascade() {
        Session s = session();
        sessions.save(s, questionnaire());
        versions.save(new SpecificationVersion(UUID.randomUUID(), s.id(), 1, Specs.valid(), "fp", VersionOrigin.GENERATED,
                "md", T0));
        sessions.delete(s.id());
        assertThat(sessions.find(s.id())).isEmpty();
        assertThat(sessions.questionnaire(s.id())).isEmpty();
        assertThat(versions.list(s.id())).isEmpty();
    }
}
