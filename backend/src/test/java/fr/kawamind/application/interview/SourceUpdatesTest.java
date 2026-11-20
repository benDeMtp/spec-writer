package fr.kawamind.application.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.application.specification.OperationRegistry;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.interview.InterviewPoint;
import fr.kawamind.domain.interview.SourceUpdate;
import fr.kawamind.domain.knowledge.KnowledgeItem;
import fr.kawamind.domain.knowledge.KnowledgeKind;
import fr.kawamind.domain.knowledge.SourceOrigin;
import fr.kawamind.domain.port.KnowledgeIndex;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Need;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.specification.Anchor;
import fr.kawamind.domain.specification.SourceDeviation;
import fr.kawamind.domain.specification.SourceRef;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.SpecificationVersion;
import fr.kawamind.domain.specification.Specs;
import fr.kawamind.domain.specification.VersionOrigin;
import fr.kawamind.support.FixedClock;
import fr.kawamind.support.InMemoryInterviews;
import fr.kawamind.support.InMemoryKnowledgeIndex;
import fr.kawamind.support.InMemorySessions;
import fr.kawamind.support.InMemorySourceUpdates;
import fr.kawamind.support.InMemorySpecifications;
import fr.kawamind.support.SequentialIdGenerator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 021, US6 : « à reporter » vers les sources de vérité (research.md R8). */
class SourceUpdatesTest {

    static final UUID PROJECT = new UUID(9, 9);
    static final String RULES = "https://wiki/pages/42";
    static final String LIMITS = "https://wiki/pages/43";
    static final String GLOSSARY = "https://wiki/pages/44";

    final FixedClock clock = new FixedClock();
    final SequentialIdGenerator ids = new SequentialIdGenerator();
    final InMemorySessions sessions = new InMemorySessions();
    final InMemorySpecifications versions = new InMemorySpecifications();
    final InMemoryInterviews interviews = new InMemoryInterviews();
    final InMemoryKnowledgeIndex knowledge = new InMemoryKnowledgeIndex();
    final InMemorySourceUpdates repository = new InMemorySourceUpdates();
    final RunInterview run = new RunInterview(interviews, sessions, versions, new OperationRegistry(), clock, ids);
    final SourceUpdates updates = new SourceUpdates(repository, interviews, sessions, versions, knowledge, clock, ids);
    Session session;
    UUID interview;

    void page(String ref, String title, String body) {
        knowledge.save(KnowledgeItem.docSection(PROJECT, ref, 0, title, body, null, clock.now(), clock.now()), null, "m");
    }

    @BeforeEach
    void setUp() {
        page(RULES, "Règles de facturation", "Un avoir est plafonné.");
        page(LIMITS, "Plafonds", "500 euros.");
        page(GLOSSARY, "Glossaire", "Avoir : remboursement partiel.");
        session = Session.start(UUID.randomUUID(), PROJECT,
                Need.of("Le gestionnaire doit pouvoir émettre un avoir partiel sur une facture."), "Avoirs", clock.now());
        sessions.save(session, Questionnaire.empty());
        var cited = new SourceDeviation.Cited(LIMITS, SourceOrigin.CONFLUENCE, "Plafonds", null);
        SpecificationContent content = new SpecificationContent("Avoirs", "Le gestionnaire émet un avoir.",
                List.of(Specs.useCase("UC-01", SourceRef.knowledge(SourceRef.Kind.DOC_PAGE, RULES, "Règles de facturation")),
                        Specs.useCase("UC-02", new SourceRef(SourceRef.Kind.NEED_EXCERPT, null, "avoir partiel"))),
                List.of(), List.of(), List.of())
                .withSourceDeviations(List.of(new SourceDeviation(cited, cited, "500 contre 1 000 euros")));
        versions.save(new SpecificationVersion(UUID.randomUUID(), session.id(), 1, content, "fp", VersionOrigin.GENERATED,
                "# v1", clock.now()));
        interview = run.startForSpecification(session.id(), List.of()).interview().id();
    }

    void point(InterviewPoint.Origin origin, String code, Anchor anchor, String text, InterviewPoint.Outcome outcome,
            InterviewPoint.DesignatedSource source) {
        run.savePoint(interview, null, new RunInterview.PointInput(session.id(), anchor, origin, code, text, outcome, null,
                null, source));
    }

    @Test
    void creesALApplicationPourLesDecisionsQuiTouchentUneSource() {
        point(InterviewPoint.Origin.REMARK, null, new Anchor("UC-01", 1, false), "Plafonné au montant TTC facturé.",
                InterviewPoint.Outcome.CHANGE, null);
        point(InterviewPoint.Origin.REMARK, null, new Anchor("UC-02", 1, false), "Sur le besoin seulement.",
                InterviewPoint.Outcome.CHANGE, null);
        point(InterviewPoint.Origin.REMARK, null, new Anchor("UC-01", 2, false), "Bien vu.",
                InterviewPoint.Outcome.CONFIRMED, null);
        point(InterviewPoint.Origin.SOURCE_DEVIATION, "EC-01", null, "", InterviewPoint.Outcome.CONFIRMED, null);
        point(InterviewPoint.Origin.REMARK, null, new Anchor("UC-02", 1, false), "Le glossaire dit « avoir partiel ».",
                null, new InterviewPoint.DesignatedSource("DOC_PAGE", GLOSSARY));
        point(InterviewPoint.Origin.QUESTION, null, null, "Qui valide ?", InterviewPoint.Outcome.DEFERRED, null);
        run.finish(interview);
        assertThat(repository.all()).isEmpty();

        List<SourceUpdate> created = updates.recordFor(interview, session.id());

        assertThat(created).extracting(SourceUpdate::ref, SourceUpdate::title, SourceUpdate::text).containsExactly(
                org.assertj.core.groups.Tuple.tuple(RULES, "Règles de facturation", "Plafonné au montant TTC facturé."),
                org.assertj.core.groups.Tuple.tuple(LIMITS, "Plafonds", "500 contre 1 000 euros"),
                org.assertj.core.groups.Tuple.tuple(GLOSSARY, "Glossaire", "Le glossaire dit « avoir partiel »."));
        assertThat(created).allSatisfy(u -> {
            assertThat(u.kind()).isEqualTo(SourceRef.Kind.DOC_PAGE);
            assertThat(u.fingerprint()).isNotBlank();
            assertThat(u.state()).isEqualTo(SourceUpdate.State.OPEN);
        });
    }

    @Test
    void sourceModifieeOuRetireeSignaleeEtClotureManuelle() {
        point(InterviewPoint.Origin.REMARK, null, new Anchor("UC-01", 1, false), "Plafonné au TTC.",
                InterviewPoint.Outcome.CHANGE, null);
        point(InterviewPoint.Origin.SOURCE_DEVIATION, "EC-01", null, "", InterviewPoint.Outcome.REMOVE, null);
        run.finish(interview);
        updates.recordFor(interview, session.id());
        assertThat(updates.open(session.id())).extracting(SourceUpdates.Line::sourceChanged).containsExactly(false, false);

        page(RULES, "Règles de facturation", "Un avoir est plafonné au montant TTC facturé.");
        knowledge.delete(PROJECT, new KnowledgeIndex.Key(KnowledgeKind.DOC_SECTION, LIMITS, 0));
        List<SourceUpdates.Line> lines = updates.open(session.id());
        assertThat(lines).extracting(SourceUpdates.Line::sourceChanged).containsExactly(true, true);

        updates.close(session.id(), lines.getFirst().update().id());
        assertThat(updates.open(session.id())).hasSize(1);
        assertThatThrownBy(() -> updates.close(UUID.randomUUID(), lines.get(1).update().id()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void uneSourceDesigneeEstDansLaConnaissanceDuProjet() {
        updates.requireKnown(session.id(), new InterviewPoint.DesignatedSource("DOC_PAGE", GLOSSARY));
        assertThatThrownBy(() -> updates.requireKnown(session.id(),
                new InterviewPoint.DesignatedSource("DOC_PAGE", "https://wiki/pages/99")))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> updates.requireKnown(session.id(),
                new InterviewPoint.DesignatedSource("ANSWER", GLOSSARY))).isInstanceOf(DomainException.class);
    }
}
