package fr.kawamind.domain.interview;

import fr.kawamind.domain.scenario.AcceptanceScenario;
import fr.kawamind.domain.session.Language;
import fr.kawamind.domain.specification.Anchor;
import fr.kawamind.domain.specification.Assumption;
import fr.kawamind.domain.specification.OpenQuestion;
import fr.kawamind.domain.specification.Requirement;
import fr.kawamind.domain.specification.SourceDeviation;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.UseCase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Parcours d'une séance (021, FR-003) : les arrêts dans l'ordre de la spécification rendue (résumé ;
 * chaque use case, ses étapes, ses alternatives et ses scénarios ; exigences fonctionnelles puis
 * techniques ; « Non rattachés »), chacun avec les points qui s'y rattachent. Les spécifications se
 * suivent dans l'ordre donné ; les points écartés à la préparation n'y figurent pas.
 */
public record Walkthrough(List<Stop> stops) {

    /** Élément d'arrêt des points sans ancre valide. */
    public static final String UNANCHORED = "UNANCHORED";

    public enum Kind {
        SUMMARY,
        USE_CASE,
        STEP,
        ALTERNATIVE,
        SCENARIO,
        REQUIREMENT,
        UNANCHORED
    }

    /** Une spécification de la séance : sa version relue, ses exclusions, ses points notés. */
    public record SpecInput(UUID sessionId, SpecificationContent content, Set<String> excluded,
            List<InterviewPoint> points) {
    }

    /** Un arrêt : l'élément, son texte, et ses points. */
    public record Stop(UUID sessionId, Anchor anchor, Kind kind, String label, String text, List<Item> items) {
    }

    /**
     * Un point à l'arrêt : un point de la spécification ({@code code} et {@code statement}, avec la
     * réponse de séance éventuelle dans {@code point}), ou un point créé en séance ({@code code} nul).
     */
    public record Item(InterviewPoint.Origin origin, String code, String statement, InterviewPoint point) {

        /** Point de la spécification pas encore traité. */
        public boolean pending() {
            return origin.fromSpecification() && (point == null || point.outcome() == null);
        }
    }

    /** Points tranchés ou reportés de la spécification, remarques et questions, points restants. */
    public record Counts(int handled, int notes, int pending) {
    }

    public static Walkthrough of(List<SpecInput> specs, Language language) {
        List<Stop> stops = new ArrayList<>();
        for (SpecInput spec : specs) {
            stops.addAll(stops(spec, language));
        }
        return new Walkthrough(List.copyOf(stops));
    }

    /** Premier arrêt après {@code from} qui porte un point non traité. */
    public OptionalInt nextPending(int from) {
        for (int i = from + 1; i < stops.size(); i++) {
            if (stops.get(i).items().stream().anyMatch(Item::pending)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    public Counts counts() {
        int handled = 0;
        int notes = 0;
        int pending = 0;
        for (Stop stop : stops) {
            for (Item item : stop.items()) {
                if (!item.origin().fromSpecification()) {
                    notes++;
                } else if (item.pending()) {
                    pending++;
                } else {
                    handled++;
                }
            }
        }
        return new Counts(handled, notes, pending);
    }

    private static List<Stop> stops(SpecInput spec, Language language) {
        SpecificationContent c = spec.content();
        // points de la spécification et points de séance, par clé d'élément
        Map<String, List<Item>> byElement = new HashMap<>();
        List<Item> unanchored = new ArrayList<>();
        Map<String, InterviewPoint> answers = new HashMap<>();
        for (InterviewPoint p : spec.points()) {
            if (p.origin().fromSpecification()) {
                answers.put(p.originCode(), p);
            }
        }
        for (Assumption a : c.assumptions()) {
            place(byElement, unanchored, c, a.anchor(),
                    new Item(InterviewPoint.Origin.ASSUMPTION, a.code(), a.statement(), answers.get(a.code())),
                    spec.excluded().contains(a.code()));
        }
        for (SourceDeviation d : c.sourceDeviations()) {
            place(byElement, unanchored, c, d.anchor(),
                    new Item(InterviewPoint.Origin.SOURCE_DEVIATION, d.code(), d.difference(), answers.get(d.code())),
                    spec.excluded().contains(d.code()));
        }
        for (OpenQuestion q : c.openQuestions()) {
            place(byElement, unanchored, c, q.anchor(),
                    new Item(InterviewPoint.Origin.OPEN_QUESTION, q.code(), q.question(), answers.get(q.code())),
                    spec.excluded().contains(q.code()));
        }
        for (InterviewPoint p : spec.points()) {
            if (!p.origin().fromSpecification()) {
                place(byElement, unanchored, c, p.anchor(), new Item(p.origin(), null, null, p), false);
            }
        }

        List<Stop> stops = new ArrayList<>();
        UUID sid = spec.sessionId();
        stops.add(stop(sid, new Anchor(Anchor.SUMMARY, null, false), Kind.SUMMARY, c.summary(), byElement, language));
        for (UseCase uc : c.useCases()) {
            stops.add(stop(sid, new Anchor(uc.code(), null, false), Kind.USE_CASE, uc.title(), byElement, language));
            for (int i = 0; i < uc.nominalFlow().size(); i++) {
                stops.add(stop(sid, new Anchor(uc.code(), i + 1, false), Kind.STEP, uc.nominalFlow().get(i), byElement,
                        language));
            }
            for (int i = 0; i < uc.alternatives().size(); i++) {
                stops.add(stop(sid, new Anchor(uc.code(), i + 1, true), Kind.ALTERNATIVE, uc.alternatives().get(i),
                        byElement, language));
            }
            for (AcceptanceScenario sc : c.scenarios()) {
                if (uc.code().equals(sc.useCaseCode())) {
                    stops.add(stop(sid, new Anchor(sc.code(), null, false), Kind.SCENARIO, sc.title(), byElement,
                            language));
                }
            }
        }
        Set<String> useCases = new java.util.HashSet<>();
        c.useCases().forEach(uc -> useCases.add(uc.code()));
        for (AcceptanceScenario sc : c.scenarios()) {
            if (!useCases.contains(sc.useCaseCode())) {
                stops.add(stop(sid, new Anchor(sc.code(), null, false), Kind.SCENARIO, sc.title(), byElement, language));
            }
        }
        for (Requirement.Category category : List.of(Requirement.Category.FUNCTIONAL, Requirement.Category.TECHNICAL)) {
            for (Requirement r : c.requirements()) {
                if (r.category() == category) {
                    stops.add(stop(sid, new Anchor(r.code(), null, false), Kind.REQUIREMENT, r.statement(), byElement,
                            language));
                }
            }
        }
        if (!unanchored.isEmpty()) {
            stops.add(new Stop(sid, new Anchor(UNANCHORED, null, false), Kind.UNANCHORED,
                    language == Language.EN ? "Not attached" : "Non rattachés", "", List.copyOf(unanchored)));
        }
        return stops;
    }

    private static void place(Map<String, List<Item>> byElement, List<Item> unanchored, SpecificationContent c,
            Anchor anchor, Item item, boolean excluded) {
        if (excluded) {
            return;
        }
        Anchor valid = Anchor.orNull(anchor, c);
        if (valid == null) {
            unanchored.add(item);
        } else {
            byElement.computeIfAbsent(key(valid), k -> new ArrayList<>()).add(item);
        }
    }

    private static Stop stop(UUID sessionId, Anchor anchor, Kind kind, String text, Map<String, List<Item>> byElement,
            Language language) {
        return new Stop(sessionId, anchor, kind, anchor.label(language), text == null ? "" : text,
                List.copyOf(byElement.getOrDefault(key(anchor), List.of())));
    }

    private static String key(Anchor a) {
        return a.element() + "|" + (a.step() == null ? "" : a.step()) + "|" + a.alternative();
    }
}
