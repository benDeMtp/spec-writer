package dev.specwriter.domain.question;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.Contradiction;
import dev.specwriter.domain.llm.ProposedQuestion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Questionnaire d'une session : lots de 5 questions au maximum, couverture des thèmes de FR-003,
 * réponses, questions passées et progression. Immuable.
 */
public record Questionnaire(List<Question> questions, Set<QuestionTheme> coveredByNeed) {

    public static final int BATCH_SIZE = 5;

    /** Thèmes à couvrir quand le besoin ne les renseigne pas déjà (FR-003). */
    public static final Set<QuestionTheme> REQUIRED_THEMES = EnumSet.complementOf(EnumSet.of(QuestionTheme.CONTRADICTION));

    public record Progress(int answered, int skipped, int total) {
    }

    public Questionnaire {
        questions = List.copyOf(questions);
        coveredByNeed = coveredByNeed.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(coveredByNeed));
    }

    public static Questionnaire empty() {
        return new Questionnaire(List.of(), Set.of());
    }

    /**
     * Ajoute un lot proposé par le LLM. Les questions dont le thème est déjà couvert par le besoin ne
     * sont pas ajoutées (FR-002) ; les contradictions sont posées en tête du lot (FR-008).
     */
    public Questionnaire addBatch(List<ProposedQuestion> proposed, List<Contradiction> contradictions,
            Set<QuestionTheme> covered, Supplier<UUID> ids) {
        Set<QuestionTheme> allCovered = EnumSet.noneOf(QuestionTheme.class);
        allCovered.addAll(coveredByNeed);
        allCovered.addAll(covered);
        allCovered.remove(QuestionTheme.CONTRADICTION);
        int batch = questions.stream().mapToInt(Question::batch).max().orElse(0) + 1;
        int order = questions.size();
        List<Question> added = new ArrayList<>();
        for (Contradiction c : contradictions) {
            if (added.size() < BATCH_SIZE) {
                added.add(new Question(ids.get(), ++order, QuestionTheme.CONTRADICTION, batch, c.questionLabel(),
                        c.description(), List.of(), null, List.of(), QuestionState.PENDING, null));
            }
        }
        for (ProposedQuestion p : proposed) {
            if (added.size() >= BATCH_SIZE || allCovered.contains(p.theme()) || isDuplicate(p.label(), added)) {
                continue;
            }
            added.add(new Question(ids.get(), ++order, p.theme(), batch, p.label(), p.rationale(), p.suggestions(),
                    p.defaultValue(), resolveDependencies(p.dependsOnQuestionIds()), QuestionState.PENDING, null));
        }
        List<Question> all = new ArrayList<>(questions);
        all.addAll(added);
        return new Questionnaire(all, allCovered);
    }

    /**
     * Ajoute les questions issues d'une réponse modifiée (FR-007). Une réponse nouvelle peut rouvrir
     * un thème que le besoin couvrait : seuls les doublons sont écartés.
     */
    public Questionnaire addRevisionQuestions(List<ProposedQuestion> proposed, Supplier<UUID> ids) {
        return new Questionnaire(questions, Set.of()).addBatch(proposed, List.of(), Set.of(), ids)
                .withCovered(coveredByNeed);
    }

    private Questionnaire withCovered(Set<QuestionTheme> covered) {
        return new Questionnaire(questions, covered);
    }

    public Questionnaire answer(UUID questionId, String value, Instant at) {
        return replace(find(questionId).answered(new Answer(questionId, value, false, at)));
    }

    /** Passe la question : la valeur par défaut est retenue et tracée comme hypothèse (FR-006). */
    public Questionnaire skip(UUID questionId, Instant at) {
        Question q = find(questionId);
        if (q.isContradiction()) {
            throw new DomainException(ErrorCode.CONTRADICTION_UNRESOLVED);
        }
        String value = q.defaultValue() == null ? Question.UNSPECIFIED : q.defaultValue();
        return replace(q.answered(new Answer(questionId, value, true, at)));
    }

    /** Questions sans objet après une modification de réponse (FR-007). */
    public Questionnaire markObsolete(Set<UUID> ids) {
        return new Questionnaire(questions.stream().map(q -> ids.contains(q.id()) && q.isActive() ? q.obsolete() : q)
                .toList(), coveredByNeed);
    }

    /** Conclusion (FR-009) : les questions restantes sont passées avec leur valeur par défaut. */
    public Questionnaire conclude(Instant at) {
        if (hasPendingContradiction()) {
            throw new DomainException(ErrorCode.CONTRADICTION_UNRESOLVED);
        }
        Questionnaire out = this;
        for (Question q : pending()) {
            out = out.skip(q.id(), at);
        }
        return out;
    }

    public Optional<Question> question(UUID id) {
        return questions.stream().filter(q -> q.id().equals(id)).findFirst();
    }

    public List<Question> active() {
        return questions.stream().filter(Question::isActive).toList();
    }

    public List<Question> pending() {
        return questions.stream().filter(q -> q.state() == QuestionState.PENDING).toList();
    }

    /** Lot affiché : les questions en attente, dans l'ordre (FR-004). */
    public List<Question> currentBatch() {
        return pending();
    }

    public List<Question> treated() {
        return questions.stream()
                .filter(q -> q.state() == QuestionState.ANSWERED || q.state() == QuestionState.SKIPPED).toList();
    }

    public boolean hasPendingContradiction() {
        return pending().stream().anyMatch(Question::isContradiction);
    }

    public Progress progress() {
        int answered = (int) active().stream().filter(q -> q.state() == QuestionState.ANSWERED).count();
        int skipped = (int) active().stream().filter(q -> q.state() == QuestionState.SKIPPED).count();
        return new Progress(answered, skipped, active().size());
    }

    /** Plus de questions passées que de questions répondues : la spécification reposera sur des hypothèses. */
    public boolean mostlyAssumptions() {
        Progress p = progress();
        return p.skipped() > 0 && p.skipped() >= p.answered();
    }

    /** Thèmes encore à explorer, transmis au LLM (contracts/llm-port.md, proposeQuestions). */
    public Set<QuestionTheme> remainingThemes() {
        Set<QuestionTheme> remaining = EnumSet.copyOf(REQUIRED_THEMES);
        remaining.removeAll(coveredByNeed);
        remaining.removeAll(active().stream().map(Question::theme).collect(Collectors.toSet()));
        return remaining;
    }

    private boolean isDuplicate(String label, List<Question> added) {
        String normalized = label == null ? "" : label.strip().toLowerCase(Locale.ROOT);
        // les questions retirées comptent aussi : une question devenue sans objet ne revient pas
        return java.util.stream.Stream.concat(questions.stream(), added.stream())
                .anyMatch(q -> q.label().toLowerCase(Locale.ROOT).equals(normalized));
    }

    /** Le LLM ne connaît les questions que par leur identifiant ; les références inconnues sont ignorées. */
    private List<UUID> resolveDependencies(List<String> references) {
        Map<String, UUID> known = questions.stream().collect(Collectors.toMap(q -> q.id().toString(), Question::id));
        return references.stream().map(known::get).filter(java.util.Objects::nonNull).distinct().toList();
    }

    private Question find(UUID id) {
        Question q = question(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (!q.isActive()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Cette question n'est plus d'actualité.");
        }
        return q;
    }

    private Questionnaire replace(Question updated) {
        return new Questionnaire(questions.stream().map(q -> q.id().equals(updated.id()) ? updated : q).toList(),
                coveredByNeed);
    }
}
