package dev.specwriter.application.session;

import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Session;
import java.util.List;

/** État d'une session après une action, avec les avertissements à afficher. */
public record SessionSnapshot(Session session, Questionnaire questionnaire, List<String> warnings) {

    public SessionSnapshot {
        warnings = List.copyOf(warnings);
    }
}
