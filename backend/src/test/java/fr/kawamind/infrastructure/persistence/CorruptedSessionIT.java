package fr.kawamind.infrastructure.persistence;

import static fr.kawamind.support.LocalApi.api;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.llm.ProposedQuestion;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.question.QuestionTheme;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Need;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.session.SessionListing;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Edge case « fichier corrompu » : la session est signalée, rien ne plante, les autres restent accessibles. */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class CorruptedSessionIT {

    @Inject
    SessionRepository sessions;

    @Inject
    EntityManager em;

    @Transactional
    void corrupt(UUID sessionId) {
        em.createNativeQuery("update question set depends_on = '{pas du json' where session_id = ?1")
                .setParameter(1, sessionId.toString()).executeUpdate();
    }

    Session saved() {
        Session s = Session.start(UUID.randomUUID(), Project.DEFAULT_ID,
                Need.of("Les employés doivent pouvoir réserver une salle de réunion pour une durée donnée."), null,
                Instant.parse("2026-10-30T09:00:00Z"));
        sessions.save(s, Questionnaire.empty().addBatch(List.of(new ProposedQuestion(QuestionTheme.GOAL, "Objectif ?",
                "Pour cadrer", List.of(), null, List.of())), List.of(), Set.of(), UUID::randomUUID));
        return s;
    }

    @Test
    void sessionIllisibleSignaleeSansBloquerLesAutres() {
        Session damaged = saved();
        Session healthy = saved();
        corrupt(damaged.id());

        List<SessionListing> list = sessions.list();
        assertThat(list).filteredOn(l -> l.id().equals(damaged.id())).singleElement()
                .satisfies(l -> assertThat(l.readable()).isFalse());
        assertThat(list).filteredOn(l -> l.id().equals(healthy.id())).singleElement()
                .satisfies(l -> assertThat(l.readable()).isTrue());
        assertThat(sessions.questionnaire(healthy.id())).isPresent();
        assertThatThrownBy(() -> sessions.questionnaire(damaged.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.SESSION_UNREADABLE));

        api().get("/api/sessions").then().statusCode(200);
        api().get("/api/sessions/" + damaged.id()).then().statusCode(422).body("code", equalTo("SESSION_UNREADABLE"));
        // une session illisible peut être supprimée
        api().delete("/api/sessions/" + damaged.id()).then().statusCode(204);
        assertThat(sessions.list()).noneMatch(l -> l.id().equals(damaged.id()));
    }
}
