package dev.specwriter.application.knowledge;

import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.ProjectContext;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.session.Session;
import java.util.List;

/**
 * Connaissance du projet transmise aux opérations de l'IA d'une session (research.md R24) : pour
 * les questions, sans analyse d'architecture ; pour une spécification, avec les faits calculés.
 */
public interface SessionKnowledge {

    ProjectKnowledge forQuestions(Session session, List<QuestionAndAnswer> answers);

    ProjectKnowledge forSpecification(Session session, List<QuestionAndAnswer> answers);

    /** Fiche générale seule, sans index : pour les cas d'usage construits sans la connaissance. */
    static SessionKnowledge sheetOnly(ProjectRepository projects) {
        return new SessionKnowledge() {
            @Override
            public ProjectKnowledge forQuestions(Session session, List<QuestionAndAnswer> answers) {
                return sheet(projects, session);
            }

            @Override
            public ProjectKnowledge forSpecification(Session session, List<QuestionAndAnswer> answers) {
                return sheet(projects, session);
            }
        };
    }

    /** Fiche générale du projet de la session (FR-008), 20 000 caractères au plus. */
    static ProjectKnowledge sheet(ProjectRepository projects, Session session) {
        ProjectContext context = session.projectId() == null ? ProjectContext.NONE
                : projects.find(session.projectId()).map(Project::context).orElse(ProjectContext.NONE);
        return ProjectKnowledge.sheet(context.markdown(), context.updatedAt());
    }
}
