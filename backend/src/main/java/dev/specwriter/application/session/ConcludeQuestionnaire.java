package dev.specwriter.application.session;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Language;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.session.SpecificationLock;
import java.util.List;
import java.util.UUID;

/**
 * Termine le questionnaire à tout moment (FR-009) : les questions restantes sont passées avec leur
 * valeur par défaut. Avertit quand la spécification reposera surtout sur des hypothèses.
 */
public class ConcludeQuestionnaire {

    static final String MOSTLY_ASSUMPTIONS_FR = "La spécification reposera majoritairement sur des hypothèses : "
            + "elles seront toutes listées et signalées.";
    static final String MOSTLY_ASSUMPTIONS_EN = "The specification will mostly rely on assumptions: all of them will "
            + "be listed and flagged.";

    private final SessionRepository sessions;
    private final ClockPort clock;

    public ConcludeQuestionnaire(SessionRepository sessions, ClockPort clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    public SessionSnapshot run(UUID sessionId) {
        Session session = sessions.find(sessionId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        SpecificationLock.requireEditable(sessions, sessionId);
        Questionnaire questionnaire = sessions.questionnaire(sessionId).orElseThrow().conclude(clock.now());
        session = session.readyToGenerate(clock.now());
        sessions.save(session, questionnaire);
        return new SessionSnapshot(session, questionnaire, warnings(session, questionnaire));
    }

    /** Avertissements d'une session prête à générer (aussi affichés à la relecture). */
    public static List<String> warnings(Session session, Questionnaire questionnaire) {
        if (!questionnaire.mostlyAssumptions()) {
            return List.of();
        }
        return List.of(session.language() == Language.EN ? MOSTLY_ASSUMPTIONS_EN : MOSTLY_ASSUMPTIONS_FR);
    }
}
