package dev.specwriter.application.specification;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.scenario.ScenarioStructureValidator;
import dev.specwriter.domain.session.Language;
import dev.specwriter.domain.specification.IdentifierAssigner;
import dev.specwriter.domain.specification.SpecificationContent;
import dev.specwriter.domain.specification.StructureViolations;
import dev.specwriter.domain.specification.TraceabilityValidator;
import java.util.List;
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

    private DraftValidation() {
    }

    /**
     * @param call appel au LLM, qui reçoit la correction à transmettre ({@code null} au premier essai)
     * @param failure code renvoyé quand les tentatives sont épuisées
     */
    public static SpecificationContent produce(Function<String, DraftSpecification> call, List<Question> treated,
            Language language, ErrorCode failure) {
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
            StructureViolations violations = assigned.violations()
                    .and(ScenarioStructureValidator.validate(assigned.content()))
                    .and(TraceabilityValidator.validate(assigned.content(), treatedIds));
            if (violations.isEmpty()) {
                return assigned.content();
            }
            correction = String.join("\n", violations.messages());
        }
        throw new DomainException(failure);
    }

    /** Réponses citables par les sources, dans l'ordre du questionnaire. */
    public static List<QuestionAndAnswer> answers(List<Question> treated) {
        return treated.stream()
                .map(q -> new QuestionAndAnswer(q.id().toString(), q.theme(), q.label(), q.answer().value(),
                        q.answer().assumption()))
                .toList();
    }
}
