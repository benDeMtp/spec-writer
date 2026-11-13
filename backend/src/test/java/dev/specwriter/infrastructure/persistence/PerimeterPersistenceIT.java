package dev.specwriter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.port.ConversationRepository;
import dev.specwriter.domain.port.PerimeterRepository;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectName;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.sources.Module;
import dev.specwriter.domain.sources.ModuleKind;
import dev.specwriter.domain.sources.ModuleQualification;
import dev.specwriter.domain.sources.ModuleRef;
import dev.specwriter.domain.sources.Occurrence;
import dev.specwriter.domain.sources.PerimeterAnalysis;
import dev.specwriter.domain.sources.PerimeterModule;
import dev.specwriter.domain.sources.PerimeterSnapshot;
import dev.specwriter.domain.sources.PerimeterTerms;
import dev.specwriter.domain.sources.Qualification;
import dev.specwriter.domain.sources.Reason;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Analyses de périmètre en base (013, T021, research.md R9). */
@QuarkusTest
class PerimeterPersistenceIT {

    static final Instant T0 = Instant.parse("2026-11-11T09:00:00Z");
    static final UUID ROOT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Inject
    ConversationRepository conversations;

    @Inject
    ProjectRepository projects;

    @Inject
    PerimeterRepository perimeters;

    @Inject
    SessionRepository sessions;

    final List<UUID> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        created.forEach(id -> projects.find(id).ifPresent(p -> projects.delete(id)));
    }

    Conversation conversation() {
        Project p = Project.create(UUID.randomUUID(), ProjectName.of("Périmètre " + UUID.randomUUID()), null, null, T0);
        projects.save(p);
        created.add(p.id());
        Conversation c = Conversation.start(UUID.randomUUID(), p.id(), "Quels modules ?", T0);
        conversations.save(c);
        return c;
    }

    ConversationMessage answer(Conversation c, int ordinal) {
        ConversationMessage m = ConversationMessage.agentStarted(UUID.randomUUID(), c.id(), ordinal, T0);
        conversations.saveMessage(m);
        return m;
    }

    static Module module(String path, ModuleKind kind) {
        return new Module(ROOT, path, path.substring(path.lastIndexOf('/') + 1), kind, null);
    }

    static PerimeterAnalysis analysis(int ordinal, String term) {
        PerimeterModule a = PerimeterModule.found(module("libs/a", ModuleKind.MAVEN), List.of(new Reason.Term(term, 3)), 3,
                List.of(new Occurrence("libs/a/A.java", 12, term, "enum " + term + " {")));
        PerimeterModule b = PerimeterModule.found(module("modules/b", ModuleKind.MAVEN), List.of(
                new Reason.Dependency(List.of(ModuleRef.of(module("libs/a", ModuleKind.MAVEN)),
                        ModuleRef.of(module("modules/b", ModuleKind.MAVEN)))),
                new Reason.Aggregator(ModuleRef.of(module("libs/a", ModuleKind.MAVEN))),
                new Reason.Architecture("Facturation", "Notification", "envoie", "REST")), 0, List.of())
                .inArchitecture("Facturation", PerimeterModule.ArchStatus.MATCHED).asNew(true);
        return new PerimeterAnalysis(ordinal, List.of(term), List.of(new PerimeterTerms.Rejected("x",
                PerimeterTerms.Rejection.TOO_SHORT)),
                List.of(new PerimeterAnalysis.RootStatus(ROOT, "rv", PerimeterAnalysis.RootState.SCANNED)),
                PerimeterAnalysis.State.INCOMPLETE, PerimeterAnalysis.IncompleteReason.MAX_DURATION, 120, 2,
                List.of("rv/gros.json"), 4_200, List.of(a, b),
                List.of(new PerimeterAnalysis.ArchLink("Archives", "Facturation", "archive", "SFTP")), null);
    }

    @Test
    void uneAnalyseEstRelueALIdentiqueEtRemplaceeUneFoisQualifiee() {
        Conversation c = conversation();
        ConversationMessage m = answer(c, 2);
        PerimeterAnalysis first = analysis(1, "ContractStatus");
        perimeters.save(m.id(), first);
        assertThat(perimeters.list(m.id())).containsExactly(first);

        PerimeterAnalysis qualified = first.withQualifications(List.of(new ModuleQualification(ROOT + "/libs/a",
                Qualification.IMPACTED, "Définit l'énumération.")));
        perimeters.save(m.id(), qualified);
        assertThat(perimeters.list(m.id())).containsExactly(qualified);
    }

    @Test
    void laDerniereAnalyseDeLaConversationEtLeNettoyage() {
        Conversation c = conversation();
        ConversationMessage older = answer(c, 2);
        ConversationMessage newer = answer(c, 4);
        perimeters.save(older.id(), analysis(1, "Ancien"));
        perimeters.save(newer.id(), analysis(1, "Premier"));
        perimeters.save(newer.id(), analysis(3, "Dernier"));
        perimeters.save(newer.id(), PerimeterAnalysis.noRoots(4, List.of("Ignoré")));
        assertThat(perimeters.last(c.id())).hasValueSatisfying(a -> assertThat(a.terms()).containsExactly("Dernier"));
        assertThat(perimeters.list(newer.id())).extracting(PerimeterAnalysis::ordinal).containsExactly(1, 3, 4);

        perimeters.clear(newer.id());
        assertThat(perimeters.list(newer.id())).isEmpty();
        assertThat(perimeters.last(c.id())).hasValueSatisfying(a -> assertThat(a.terms()).containsExactly("Ancien"));

        conversations.delete(c.id());
        assertThat(perimeters.list(older.id())).isEmpty();
        assertThat(perimeters.last(c.id())).isEmpty();
    }

    @Test
    void lInstantaneDeLaSessionEstReluALIdentiqueEtSupprimeAvecElle() {
        Session session = Session.start(UUID.randomUUID(), Project.DEFAULT_ID,
                Need.of("Le gestionnaire doit pouvoir suspendre un contrat d'abonnement puis en reprendre la facturation."), null, T0);
        sessions.save(session, Questionnaire.empty());
        assertThat(perimeters.snapshot(session.id())).isEmpty();
        PerimeterSnapshot snapshot = PerimeterSnapshot.of(analysis(1, "ContractStatus"));
        perimeters.saveSnapshot(session.id(), snapshot, T0);
        perimeters.saveSnapshot(session.id(), snapshot, T0);
        assertThat(perimeters.snapshot(session.id())).contains(snapshot);

        sessions.delete(session.id());
        assertThat(perimeters.snapshot(session.id())).isEmpty();
    }
}
