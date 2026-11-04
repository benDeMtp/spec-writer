package dev.specwriter.application.knowledge;

import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.ContextSheets;
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

    /**
     * Fiche générale du projet de la session, 20 000 caractères au plus. En attendant la fusion des
     * deux fiches (T113), c'est la fiche fonctionnelle suivie de la fiche technique.
     */
    static ProjectKnowledge sheet(ProjectRepository projects, Session session) {
        ContextSheets sheets = session.projectId() == null ? ContextSheets.NONE
                : projects.find(session.projectId()).map(Project::contexts).orElse(ContextSheets.NONE);
        String functional = sheets.functional().markdown();
        String technical = sheets.technical().markdown();
        String sheet = functional.isBlank() ? technical
                : technical.isBlank() ? functional : functional + "\n\n" + technical;
        if (sheet.length() > ProjectKnowledge.MAX_SHEET) {
            sheet = sheet.substring(0, ProjectKnowledge.MAX_SHEET);
        }
        return ProjectKnowledge.sheet(sheet, sheets.updatedAt());
    }
}
