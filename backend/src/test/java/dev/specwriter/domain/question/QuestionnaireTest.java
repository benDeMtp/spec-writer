package dev.specwriter.domain.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.Contradiction;
import dev.specwriter.domain.llm.ProposedQuestion;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class QuestionnaireTest {

    static final Instant AT = Instant.parse("2026-10-30T09:00:00Z");

    final AtomicInteger n = new AtomicInteger();
    final Supplier<UUID> ids = () -> new UUID(0, n.incrementAndGet());

    static ProposedQuestion q(QuestionTheme theme, String label) {
        return new ProposedQuestion(theme, label, "Pour lever une ambiguïté", List.of("Oui", "Non"), "Oui", List.of());
    }

    Questionnaire withBatch(ProposedQuestion... questions) {
        return Questionnaire.empty().addBatch(List.of(questions), List.of(), Set.of(), ids);
    }

    @Test
    void lotDeCinqQuestionsAuMaximum() {
        List<ProposedQuestion> seven = IntStream.range(0, 7)
                .mapToObj(i -> q(QuestionTheme.BUSINESS_RULES, "Règle " + i + " ?")).toList();
        Questionnaire qn = Questionnaire.empty().addBatch(seven, List.of(), Set.of(), ids);
        assertThat(qn.currentBatch()).hasSize(5).allMatch(x -> x.batch() == 1);
        assertThat(qn.currentBatch()).extracting(Question::order).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void themeDejaCouvertParLeBesoinNonAjoute() {
        Questionnaire qn = Questionnaire.empty().addBatch(
                List.of(q(QuestionTheme.ACTORS, "Qui réserve ?"), q(QuestionTheme.ERRORS, "Et si la salle est prise ?")),
                List.of(), Set.of(QuestionTheme.ACTORS), ids);
        assertThat(qn.active()).extracting(Question::theme).containsExactly(QuestionTheme.ERRORS);
        assertThat(qn.remainingThemes()).doesNotContain(QuestionTheme.ACTORS, QuestionTheme.ERRORS)
                .contains(QuestionTheme.GOAL);
        // un lot suivant ne réintroduit pas le thème couvert
        Questionnaire next = qn.addBatch(List.of(q(QuestionTheme.ACTORS, "Qui valide ?")), List.of(), Set.of(), ids);
        assertThat(next.active()).hasSize(1);
    }

    @Test
    void questionEnDoubleIgnoree() {
        Questionnaire qn = withBatch(q(QuestionTheme.GOAL, "Quel objectif ?"), q(QuestionTheme.ERRORS, "quel objectif ?"));
        assertThat(qn.active()).hasSize(1);
    }

    @Test
    void reglesDUneQuestion() {
        assertThatThrownBy(() -> withBatch(new ProposedQuestion(QuestionTheme.GOAL, "Quel objectif ?", " ", List.of(),
                null, List.of()))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> withBatch(q(QuestionTheme.GOAL, "x".repeat(501)))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> withBatch(new ProposedQuestion(QuestionTheme.GOAL, "Quel objectif ?", "Pour savoir",
                List.of("a", "b", "c", "d", "e", "f"), null, List.of()))).isInstanceOf(DomainException.class);
    }

    @Test
    void reponseEtTransitions() {
        Questionnaire qn = withBatch(q(QuestionTheme.GOAL, "Quel objectif ?"));
        UUID id = qn.active().getFirst().id();
        Questionnaire answered = qn.answer(id, "Éviter les doubles réservations", AT);
        Question q = answered.question(id).orElseThrow();
        assertThat(q.state()).isEqualTo(QuestionState.ANSWERED);
        assertThat(q.answer().assumption()).isFalse();
        assertThat(answered.currentBatch()).isEmpty();

        assertThatThrownBy(() -> qn.answer(id, "x".repeat(5001), AT)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> qn.answer(id, "  ", AT)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> qn.answer(UUID.randomUUID(), "x", AT))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));

        Questionnaire obsolete = answered.markObsolete(Set.of(id));
        assertThat(obsolete.question(id).orElseThrow().state()).isEqualTo(QuestionState.OBSOLETE);
        assertThat(obsolete.active()).isEmpty();
        assertThatThrownBy(() -> obsolete.answer(id, "x", AT)).isInstanceOf(DomainException.class);
    }

    @Test
    void questionPasseeRetientLaValeurParDefautCommeHypothese() {
        Questionnaire qn = withBatch(q(QuestionTheme.GOAL, "Quel objectif ?"),
                new ProposedQuestion(QuestionTheme.ERRORS, "Que faire en cas d'erreur ?", "Cas d'erreur", List.of(), null,
                        List.of()));
        UUID first = qn.active().get(0).id();
        UUID second = qn.active().get(1).id();
        Questionnaire skipped = qn.skip(first, AT).skip(second, AT);
        Answer a = skipped.question(first).orElseThrow().answer();
        assertThat(a.assumption()).isTrue();
        assertThat(a.value()).isEqualTo("Oui");
        assertThat(skipped.question(second).orElseThrow().answer().value()).isEqualTo(Question.UNSPECIFIED);
        assertThat(skipped.question(first).orElseThrow().state()).isEqualTo(QuestionState.SKIPPED);
    }

    @Test
    void contradictionPoseeEnTeteEtObligatoire() {
        Questionnaire qn = Questionnaire.empty().addBatch(List.of(q(QuestionTheme.GOAL, "Quel objectif ?")),
                List.of(new Contradiction("Le besoin dit gratuit, une réponse dit payant", "Le service est-il payant ?")),
                Set.of(), ids);
        Question contradiction = qn.currentBatch().getFirst();
        assertThat(contradiction.theme()).isEqualTo(QuestionTheme.CONTRADICTION);
        assertThat(qn.hasPendingContradiction()).isTrue();
        assertThatThrownBy(() -> qn.skip(contradiction.id(), AT)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONTRADICTION_UNRESOLVED));
        assertThatThrownBy(() -> qn.conclude(AT)).isInstanceOf(DomainException.class);
        Questionnaire resolved = qn.answer(contradiction.id(), "Payant", AT).conclude(AT);
        assertThat(resolved.pending()).isEmpty();
        assertThat(resolved.hasPendingContradiction()).isFalse();
    }

    @Test
    void progressionEtMajoriteDHypotheses() {
        Questionnaire qn = withBatch(q(QuestionTheme.GOAL, "A ?"), q(QuestionTheme.ERRORS, "B ?"), q(QuestionTheme.EDGE_CASES, "C ?"));
        List<Question> qs = qn.active();
        Questionnaire step = qn.answer(qs.get(0).id(), "oui", AT).skip(qs.get(1).id(), AT);
        assertThat(step.progress()).isEqualTo(new Questionnaire.Progress(1, 1, 3));
        assertThat(step.mostlyAssumptions()).isTrue();
        assertThat(step.treated()).hasSize(2);
        Questionnaire concluded = step.conclude(AT);
        assertThat(concluded.progress()).isEqualTo(new Questionnaire.Progress(1, 2, 3));
        assertThat(qn.answer(qs.get(0).id(), "oui", AT).mostlyAssumptions()).isFalse();
    }

    @Test
    void dependancesResoluesSurLesQuestionsConnues() {
        Questionnaire qn = withBatch(q(QuestionTheme.GOAL, "A ?"));
        String known = qn.active().getFirst().id().toString();
        Questionnaire next = qn.addBatch(List.of(new ProposedQuestion(QuestionTheme.ERRORS, "B ?", "Parce que", List.of(),
                null, List.of(known, "inconnue"))), List.of(), Set.of(), ids);
        assertThat(next.active().get(1).dependsOn()).containsExactly(qn.active().getFirst().id());
        assertThat(next.active().get(1).batch()).isEqualTo(2);
    }
}
