package fr.kawamind.domain.interview;

import fr.kawamind.domain.specification.Anchor;
import fr.kawamind.domain.specification.Assumption;
import fr.kawamind.domain.specification.OpenQuestion;
import fr.kawamind.domain.specification.SourceDeviation;
import fr.kawamind.domain.specification.SpecificationContent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Issue d'une séance appliquée à la version révisée (021, research.md R3, FR-015), sans modèle :
 * les points tranchés disparaissent, les points reportés deviennent des points ouverts. La
 * correspondance passe par {@code fromCode}, le code de l'élément dans la version de base.
 */
public final class InterviewOutcome {

    /** Un point reporté : sa question, son responsable, son échéance, son élément, son code d'origine. */
    public record Deferred(String question, String owner, LocalDate due, Anchor anchor, String originCode) {
    }

    /**
     * {@code decided} : codes de la version de base tranchés en séance (confirmés, à modifier, à
     * retirer, ou reportés : un point reporté devient un point ouvert) ; {@code deferred} : points
     * reportés.
     */
    public record Decisions(Set<String> decided, List<Deferred> deferred) {

        public Decisions {
            decided = Set.copyOf(decided);
            deferred = List.copyOf(deferred);
        }

        /** Décisions d'une séance, d'après ses points sur une spécification. */
        public static Decisions of(List<InterviewPoint> points) {
            Set<String> decided = new HashSet<>();
            List<Deferred> deferred = new ArrayList<>();
            for (InterviewPoint p : points) {
                if (p.outcome() == null) {
                    continue;
                }
                if (p.originCode() != null) {
                    decided.add(p.originCode());
                }
                if (p.outcome() == InterviewPoint.Outcome.DEFERRED) {
                    deferred.add(new Deferred(p.text(), p.owner(), p.due(), p.anchor(), p.originCode()));
                }
            }
            return new Decisions(decided, deferred);
        }
    }

    private InterviewOutcome() {
    }

    /**
     * Applique les décisions à {@code revised}, révision de {@code base}. Un {@code fromCode} inconnu
     * de la base est ignoré ; les codes {@code H-} et {@code Q-} sont renumérotés et les
     * {@code fromCode} effacés : ils ne valent que pour cette révision.
     */
    public static SpecificationContent apply(SpecificationContent revised, SpecificationContent base,
            Decisions decisions) {
        Set<String> known = codes(base);
        Set<String> decided = decisions.decided();
        // une question passée tranchée en séance : son hypothèse, que le code rajoute d'office, disparaît aussi
        Set<java.util.UUID> settled = new HashSet<>();
        base.assumptions().stream().filter(a -> decided.contains(a.code()) && a.questionId() != null)
                .forEach(a -> settled.add(a.questionId()));
        List<Assumption> assumptions = new ArrayList<>();
        for (Assumption a : revised.assumptions()) {
            String from = known.contains(a.fromCode()) ? a.fromCode() : null;
            boolean sameQuestion = a.origin() == Assumption.Origin.SKIPPED_QUESTION && settled.contains(a.questionId());
            if (!sameQuestion && (from == null || !decided.contains(from))) {
                assumptions.add(a.withFromCode(null).withCode("H-%02d".formatted(assumptions.size() + 1)));
            }
        }
        List<SourceDeviation> deviations = revised.sourceDeviations().stream()
                .filter(d -> !known.contains(d.fromCode()) || !decided.contains(d.fromCode()))
                .map(d -> d.withFromCode(null)).toList();
        List<OpenQuestion> questions = new ArrayList<>();
        Set<String> kept = new HashSet<>();
        for (OpenQuestion q : revised.openQuestions()) {
            String from = known.contains(q.fromCode()) ? q.fromCode() : null;
            if (from != null && decided.contains(from) && deferredFrom(decisions, from) == null) {
                continue;
            }
            Deferred d = from == null ? null : deferredFrom(decisions, from);
            if (d != null) {
                kept.add(from);
                q = new OpenQuestion(q.code(), q.question(), d.owner() != null ? d.owner() : q.owner(),
                        d.due() != null ? d.due() : q.due(), q.anchor(), null);
            }
            questions.add(q.withFromCode(null));
        }
        for (Deferred d : decisions.deferred()) {
            if (d.originCode() != null && kept.contains(d.originCode())) {
                continue;
            }
            questions.add(new OpenQuestion(null, d.question(), d.owner(), d.due(), Anchor.orNull(d.anchor(), revised),
                    null));
        }
        List<OpenQuestion> numbered = new ArrayList<>();
        for (OpenQuestion q : questions.subList(0, Math.min(questions.size(), SpecificationContent.MAX_OPEN_QUESTIONS))) {
            numbered.add(q.withCode("Q-%02d".formatted(numbered.size() + 1)));
        }
        return revised.withAssumptions(assumptions).withSourceDeviations(deviations).withOpenQuestions(numbered);
    }

    /**
     * La version de base reprise telle quelle (aucun changement rédigé) : chaque élément porte son
     * propre code comme {@code fromCode}, pour que l'issue de la séance s'y applique.
     */
    public static SpecificationContent unchanged(SpecificationContent base) {
        return base.withAssumptions(base.assumptions().stream().map(a -> a.withFromCode(a.code())).toList())
                .withSourceDeviations(base.sourceDeviations().stream().map(d -> d.withFromCode(d.code())).toList())
                .withOpenQuestions(base.openQuestions().stream().map(q -> q.withFromCode(q.code())).toList());
    }

    private static Deferred deferredFrom(Decisions decisions, String code) {
        return decisions.deferred().stream().filter(d -> code.equals(d.originCode())).findFirst().orElse(null);
    }

    private static Set<String> codes(SpecificationContent content) {
        Set<String> codes = new HashSet<>();
        content.assumptions().forEach(a -> codes.add(a.code()));
        content.sourceDeviations().forEach(d -> codes.add(d.code()));
        content.openQuestions().forEach(q -> codes.add(q.code()));
        return codes;
    }
}
