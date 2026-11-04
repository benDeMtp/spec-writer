package dev.specwriter.application.session;

import dev.specwriter.domain.llm.ProjectContextInput;
import dev.specwriter.domain.llm.ReassessRequest;
import dev.specwriter.domain.llm.ReassessResult;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Session;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Réévaluation après une réponse modifiée (FR-007, US3-2) : les questions devenues sans objet
 * passent à OBSOLETE, les nouvelles sont ajoutées, les autres réponses sont conservées. Seules les
 * questions actives et différentes de la question modifiée peuvent être retirées.
 */
public final class ReviseAnswer {

    public record Result(Questionnaire questionnaire, Revision revision) {
    }

    private final LlmClient llm;
    private final IdGenerator ids;

    public ReviseAnswer(LlmClient llm, IdGenerator ids) {
        this.llm = llm;
        this.ids = ids;
    }

    public Result reassess(Session session, Questionnaire questionnaire, UUID changed, ProjectContextInput projectContext) {
        ReassessResult result = llm.reassessAfterChange(new ReassessRequest(session.need().text(), session.language(),
                NextBatch.known(questionnaire), changed.toString(), projectContext));
        Set<String> cited = new HashSet<>(result.obsoleteQuestionIds());
        Set<UUID> obsolete = questionnaire.active().stream()
                .map(Question::id)
                .filter(id -> !id.equals(changed) && cited.contains(id.toString()))
                .collect(Collectors.toSet());
        Questionnaire withoutObsolete = questionnaire.markObsolete(obsolete);
        Questionnaire revised = withoutObsolete.addRevisionQuestions(result.newQuestions(), ids::next);

        Set<UUID> before = withoutObsolete.questions().stream().map(Question::id).collect(Collectors.toSet());
        List<String> removed = questionnaire.active().stream().filter(q -> obsolete.contains(q.id()))
                .map(Question::label).toList();
        List<String> added = revised.questions().stream().filter(q -> !before.contains(q.id()))
                .map(Question::label).toList();
        return new Result(revised, new Revision(removed, added));
    }
}
