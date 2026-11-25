package fr.kawamind.application.specification;

import fr.kawamind.application.knowledge.Citations;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.knowledge.KnowledgeKind;
import fr.kawamind.domain.knowledge.ProjectKnowledge;
import fr.kawamind.domain.llm.DraftSpecification;
import fr.kawamind.domain.llm.QuestionAndAnswer;
import fr.kawamind.domain.question.Question;
import fr.kawamind.domain.scenario.ScenarioStructureValidator;
import fr.kawamind.domain.session.Language;
import fr.kawamind.domain.specification.IdentifierAssigner;
import fr.kawamind.domain.specification.SourceRef;
import fr.kawamind.domain.specification.SourceDeviation;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.StructureViolations;
import fr.kawamind.domain.specification.TraceabilityValidator;
import fr.kawamind.domain.specification.UseCase;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Boucle commune aux opérations qui produisent une spécification : identifiants attribués par le
 * code, contrôle de structure et de traçabilité, et 2 nouvelles demandes au maximum avec les écarts
 * constatés. Aucune version invalide ne sort d'ici.
 */
public final class DraftValidation {

    public static final int MAX_ATTEMPTS = 3;

    /**
     * Discussion d'une spécification (011, research.md R4) : ses messages citables (référence et
     * titre) et les use cases de la version courante, qui doivent rester dans la proposition.
     */
    public record Discussion(Map<String, String> citable, List<UseCase> current) {

        public static final Discussion NONE = new Discussion(Map.of(), List.of());

        public Discussion {
            citable = Map.copyOf(citable);
            current = List.copyOf(current);
        }

        /** Une source DISCUSSION prend le titre du message cité. */
        SpecificationContent titled(SpecificationContent content) {
            List<UseCase> useCases = content.useCases().stream().map(uc -> {
                SourceRef s = uc.source();
                if (s == null || s.kind() != SourceRef.Kind.DISCUSSION || !citable.containsKey(s.ref())) {
                    return uc;
                }
                return new UseCase(uc.code(), uc.title(), uc.actor(), uc.goal(), uc.preconditions(), uc.nominalFlow(),
                        uc.alternatives(), SourceRef.knowledge(SourceRef.Kind.DISCUSSION, s.ref(), citable.get(s.ref())));
            }).toList();
            return new SpecificationContent(content.title(), content.summary(), content.details(), useCases,
                    content.scenarios(), content.assumptions(), content.requirements(), content.adrDeviations(), content.story(),
                    content.splitProposal(), content.sourceDeviations(), content.openQuestions());
        }

        /**
         * Use cases de la version courante absents de la proposition, reconnus par leur titre : les
         * codes sont attribués par position et changent quand un use case est retiré.
         */
        List<String> missing(SpecificationContent content) {
            Set<String> kept = content.useCases().stream().map(uc -> key(uc.title())).collect(Collectors.toSet());
            return current.stream().filter(uc -> !kept.contains(key(uc.title())))
                    .map(uc -> uc.code() + " « " + uc.title() + " » a disparu de la proposition : recopie-le à"
                            + " l'identique, sauf si la discussion a établi de le retirer ou de le renommer")
                    .toList();
        }

        private static String key(String title) {
            return title == null ? "" : title.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        }
    }

    private DraftValidation() {
    }

    /**
     * @param call appel au LLM, qui reçoit la correction à transmettre ({@code null} au premier essai)
     * @param failure code renvoyé quand les tentatives sont épuisées
     */
    public static SpecificationContent produce(Function<String, DraftSpecification> call, List<Question> treated,
            Language language, ErrorCode failure) {
        return produce(call, treated, language, failure, ProjectKnowledge.EMPTY);
    }

    /**
     * @param knowledge connaissance transmise : les sources PROJECT_CONTEXT citent la fiche générale
     *     mot pour mot, les autres sources et les écarts citent un élément transmis
     */
    public static SpecificationContent produce(Function<String, DraftSpecification> call, List<Question> treated,
            Language language, ErrorCode failure, ProjectKnowledge knowledge) {
        return produce(call, treated, language, failure, knowledge, Discussion.NONE);
    }

    /**
     * @param discussion messages citables et use cases à garder (011) ; un use case disparu est
     *     redemandé, mais la dernière tentative est gardée : le retrait peut être ce que la
     *     discussion a établi
     */
    public static SpecificationContent produce(Function<String, DraftSpecification> call, List<Question> treated,
            Language language, ErrorCode failure, ProjectKnowledge knowledge, Discussion discussion) {
        Set<UUID> treatedIds = treated.stream().map(Question::id).collect(Collectors.toSet());
        String correction = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            DraftSpecification draft;
            try {
                draft = call.apply(correction);
            } catch (DomainException e) {
                if (e.code() == ErrorCode.LLM_INVALID_OUTPUT && failure != ErrorCode.LLM_INVALID_OUTPUT) {
                    throw new DomainException(failure);
                }
                throw e;
            }
            IdentifierAssigner.Assigned assigned = IdentifierAssigner.assign(draft, treated, language);
            SpecificationContent content = discussion.titled(repaired(assigned.content(), knowledge));
            StructureViolations violations = assigned.violations()
                    .and(ScenarioStructureValidator.validate(content))
                    .and(TraceabilityValidator.validate(content, treatedIds, knowledge, discussion.citable().keySet()));
            List<String> missing = discussion.missing(content);
            if (violations.isEmpty() && (missing.isEmpty() || attempt == MAX_ATTEMPTS)) {
                return content;
            }
            List<String> messages = new java.util.ArrayList<>(violations.messages());
            messages.addAll(missing);
            correction = String.join("\n", messages);
        }
        throw new DomainException(failure);
    }

    /**
     * Références de la connaissance écrites autrement (casse, chemin, ancre) ramenées à l'élément
     * transmis qu'elles désignent ({@link Citations}) ; un écart à un ADR inconnu est retiré. Une
     * source restée hors de la sélection est signalée par la traçabilité, puis redemandée.
     */
    static SpecificationContent repaired(SpecificationContent content, ProjectKnowledge knowledge) {
        List<UseCase> useCases = content.useCases().stream().map(uc -> {
            SourceRef s = uc.source();
            if (s == null || s.kind().knowledgeKinds().isEmpty() || s.ref() == null) {
                return uc;
            }
            Set<String> citable = new LinkedHashSet<>();
            s.kind().knowledgeKinds().forEach(k -> citable.addAll(knowledge.citable(k)));
            String ref = Citations.resolver(citable).resolve(s.ref());
            SourceRef kept = ref == null ? s : s.withRef(ref);
            // l'extrait d'un document joint ne sort jamais de l'outil (009, research.md R6)
            kept = kept.attachment() ? kept.withoutExcerpt() : kept;
            return kept.equals(s) ? uc : new UseCase(uc.code(), uc.title(), uc.actor(), uc.goal(),
                    uc.preconditions(), uc.nominalFlow(), uc.alternatives(), kept);
        }).toList();
        Citations.Resolver adrs = Citations.resolver(knowledge.citable(KnowledgeKind.ADR));
        List<SpecificationContent.AdrDeviation> deviations = content.adrDeviations().stream()
                .map(d -> {
                    String ref = adrs.resolve(d.adrRef());
                    return ref == null ? null : new SpecificationContent.AdrDeviation(ref, d.decision(), d.deviation());
                })
                .filter(Objects::nonNull).toList();
        return new SpecificationContent(content.title(), content.summary(), content.details(), useCases,
                content.scenarios(), content.assumptions(), content.requirements(), deviations, content.story(),
                content.splitProposal(), sourceDeviations(content.sourceDeviations(), knowledge), content.openQuestions());
    }

    /**
     * Écarts entre sources (016, R8) : gardés seulement si leurs deux références désignent des
     * éléments transmis, distincts, ramenés à leur forme transmise et complétés de leur source, titre
     * et date d'après la connaissance ; le modèle n'est jamais cru sur les références.
     */
    static List<SourceDeviation> sourceDeviations(List<SourceDeviation> deviations, ProjectKnowledge knowledge) {
        if (deviations.isEmpty()) {
            return deviations;
        }
        Map<String, ProjectKnowledge.Entry> entries = new java.util.LinkedHashMap<>();
        knowledge.entries().forEach(e -> entries.putIfAbsent(e.ref(), e));
        Citations.Resolver resolver = Citations.resolver(entries.keySet());
        List<SourceDeviation> kept = new java.util.ArrayList<>();
        for (SourceDeviation d : deviations) {
            SourceDeviation.Cited reference = cited(resolver, entries, d.reference().ref());
            SourceDeviation.Cited interpretation = cited(resolver, entries, d.interpretation().ref());
            if (reference != null && interpretation != null && !reference.ref().equals(interpretation.ref())) {
                kept.add(d.withCited(reference, interpretation));
            }
        }
        return kept;
    }

    private static SourceDeviation.Cited cited(Citations.Resolver resolver, Map<String, ProjectKnowledge.Entry> entries,
            String written) {
        String ref = resolver.resolve(written);
        ProjectKnowledge.Entry e = ref == null ? null : entries.get(ref);
        if (e == null) {
            return null;
        }
        return new SourceDeviation.Cited(ref, e.provenance() == null ? null : e.provenance().origin(), e.title(),
                e.provenance() == null ? null : e.provenance().updatedAt());
    }

    /** Réponses citables par les sources, dans l'ordre du questionnaire. */
    public static List<QuestionAndAnswer> answers(List<Question> treated) {
        return treated.stream()
                .map(q -> new QuestionAndAnswer(q.id().toString(), q.theme(), q.label(), q.answer().value(),
                        q.answer().assumption()))
                .toList();
    }
}
