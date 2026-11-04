package dev.specwriter.application.session;

import dev.specwriter.domain.llm.ProjectContextInput;
import dev.specwriter.domain.llm.ProposeQuestionsRequest;
import dev.specwriter.domain.llm.ProposeQuestionsResult;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.port.IdGenerator;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Session;
import java.util.List;

/** Calcul du lot de questions suivant (FR-002 à FR-004, FR-008). */
final class NextBatch {

    /** Au-delà, le questionnaire est considéré comme complet : 30 questions au plus. */
    static final int MAX_BATCHES = 6;

    private NextBatch() {
    }

    static Questionnaire ask(LlmClient llm, IdGenerator ids, Session session, Questionnaire questionnaire,
            ProjectContextInput projectContext) {
        int batches = questionnaire.questions().stream().mapToInt(Question::batch).max().orElse(0);
        // Même quand tous les thèmes sont couverts, le LLM est consulté : il détecte les
        // contradictions entre le besoin et les réponses (FR-008).
        if (batches >= MAX_BATCHES) {
            return questionnaire;
        }
        ProposeQuestionsResult result = llm.proposeQuestions(new ProposeQuestionsRequest(session.need().text(),
                session.language(), known(questionnaire), questionnaire.remainingThemes(), projectContext));
        return questionnaire.addBatch(result.questions(), result.contradictions(), result.coveredThemes(), ids::next);
    }

    static List<QuestionAndAnswer> known(Questionnaire questionnaire) {
        return questionnaire.treated().stream()
                .map(q -> new QuestionAndAnswer(q.id().toString(), q.theme(), q.label(), q.answer().value(),
                        q.answer().assumption()))
                .toList();
    }
}
