package dev.specwriter.application.context;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ContextQuestionRepository;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.ContextQuestion;
import java.util.List;
import java.util.UUID;

/**
 * Historique des questions de clarification d'un projet (FR-013i) : consultable, et chaque réponse
 * modifiable. Une modification ne change aucune fiche avant la génération suivante.
 */
public class ManageContextQuestions {

    private final ProjectRepository projects;
    private final ContextQuestionRepository questions;
    private final ClockPort clock;

    public ManageContextQuestions(ProjectRepository projects, ContextQuestionRepository questions, ClockPort clock) {
        this.projects = projects;
        this.questions = questions;
        this.clock = clock;
    }

    public List<ContextQuestion> list(UUID projectId) {
        requireProject(projectId);
        return questions.list(projectId);
    }

    public ContextQuestion update(UUID projectId, int number, String answer, boolean skip) {
        requireProject(projectId);
        ContextQuestion updated = questions.find(projectId, number)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Cette question n'existe pas."))
                .reanswered(answer, skip, clock.now());
        questions.save(updated);
        return updated;
    }

    private void requireProject(UUID projectId) {
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
    }
}
