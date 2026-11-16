package fr.kawamind.application.session;

import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Session;
import java.util.List;

/** État d'une session après une action, avec les avertissements et l'effet d'une réponse modifiée. */
public record SessionSnapshot(Session session, Questionnaire questionnaire, List<String> warnings, Revision revision) {

    public SessionSnapshot {
        warnings = List.copyOf(warnings);
        revision = revision == null ? Revision.NONE : revision;
    }

    public SessionSnapshot(Session session, Questionnaire questionnaire, List<String> warnings) {
        this(session, questionnaire, warnings, Revision.NONE);
    }
}
