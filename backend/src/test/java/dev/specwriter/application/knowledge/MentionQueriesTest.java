package dev.specwriter.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.knowledge.MentionQueries.FieldText;
import dev.specwriter.application.knowledge.MentionQueries.MentionState;
import dev.specwriter.application.knowledge.MentionQueries.State;
import dev.specwriter.application.knowledge.MentionQueries.Suggestion;
import dev.specwriter.application.attachment.AttachmentDrafts;
import dev.specwriter.domain.attachment.Attachment;
import dev.specwriter.domain.attachment.AttachmentDraft;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.MentionField;
import dev.specwriter.domain.knowledge.MentionKind;
import dev.specwriter.domain.llm.ProposedQuestion;
import dev.specwriter.domain.port.AttachmentReader;
import dev.specwriter.domain.port.ModelCapacities;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.settings.ModelCapacity;
import dev.specwriter.support.FakeEmbedding;
import dev.specwriter.support.FixedClock;
import dev.specwriter.support.InMemoryAttachments;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import dev.specwriter.support.JiraWorld;
import dev.specwriter.support.SequentialIdGenerator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 007 : suggestions (research.md R5) et état des mentions (R8), sur l'index seul. */
class MentionQueriesTest {

    static final UUID P = Project.DEFAULT_ID;
    static final Instant T = Instant.parse("2026-11-01T08:00:00Z");
    static final String S = "docs:00000000-0000-0000-0000-000000000006/";

    final JiraWorld w = new JiraWorld();
    final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    MentionQueries queries;

    /** Document indexé en sections de 5 000 caractères au plus. */
    void doc(String path, String title, int length) {
        for (int n = 0, left = length; left > 0; n++, left -= 5_000) {
            index.save(KnowledgeItem.docSection(P, S + path + "#" + n, 0, title + " — Partie " + n,
                    "x".repeat(Math.min(5_000, left)), null, T, T), null, null);
        }
    }

    void adr(int n, AdrStatus status, String by) {
        index.save(KnowledgeItem.adr(P, "aurora/adr/%04d-decision-%d.html".formatted(n, n), 0, n + ". Décision " + n,
                "d".repeat(2_000), AdrStatus.Statement.of(status, by), T, T), null, null);
    }

    @BeforeEach
    void setUp() {
        doc("10. MVP/Documentation DC/Aurora_Matrice_Regles_Gestion_Activites_1.xlsx",
                "Aurora_Matrice_Regles_Gestion_Activites_1", 30_000);
        doc("Delivery/Suivi.xlsx", "Suivi", 1_000);
        doc("Archives/Suivi.xlsx", "Suivi", 1_000);
        doc("Delivery/FAQ_Aurora_Features.xlsx", "FAQ_Aurora_Features", 2_000);
        index.save(KnowledgeItem.docSection(P, "aurora/matrice-des-flux.html", 0, "Matrice des flux", "f".repeat(500),
                null, T, T), null, null);
        adr(1, AdrStatus.ACCEPTED, null);
        adr(2, AdrStatus.SUPERSEDED, "ADR-0001");
        adr(3, AdrStatus.DEPRECATED, null);
        AnalyzeArchitecture analyze = new AnalyzeArchitecture(index, w.llm, w.policy);
        queries = new MentionQueries(w.projects, w.sessions, index,
                new SelectKnowledge(w.projects, index, new FakeEmbedding(), analyze));
    }

    @Test
    void suggestionsClasseesEtBornees() {
        List<Suggestion> found = queries.suggest(P, "matr");
        // la page commence par « matr », le document le contient seulement : commence-par passe d'abord
        assertThat(found).extracting(Suggestion::name).containsExactly("Matrice des flux",
                "Aurora_Matrice_Regles_Gestion_Activites_1");
        assertThat(queries.suggest(P, "MATRICE D")).extracting(Suggestion::kind).containsExactly(MentionKind.PAGE);
        assertThat(found.get(1).insert()).isEqualTo("@Aurora_Matrice_Regles_Gestion_Activites_1");
        assertThat(found.get(1).path()).isEqualTo(
                "10. MVP/Documentation DC/Aurora_Matrice_Regles_Gestion_Activites_1.xlsx");
        for (int i = 0; i < 12; i++) {
            doc("Lot/Note " + i + ".docx", "Note " + i, 10);
        }
        assertThat(queries.suggest(P, "note")).hasSize(MentionQueries.MAX_SUGGESTIONS);
    }

    @Test
    void nomPartageInsereLeChemin() {
        assertThat(queries.suggest(P, "suivi")).extracting(Suggestion::insert)
                .containsExactly("@Archives/Suivi.xlsx", "@Delivery/Suivi.xlsx");
        assertThat(queries.suggest(P, "décision 1")).extracting(Suggestion::insert).containsExactly("@ADR-1");
        assertThat(queries.suggest(P, "zzz")).isEmpty();
    }

    @Test
    void saisieInvalideOuProjetInconnu() {
        assertThatThrownBy(() -> queries.suggest(P, " ")).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> queries.suggest(P, "x".repeat(201))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> queries.suggest(UUID.randomUUID(), "matr")).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }

    static List<MentionState> states(MentionQueries q, String text) {
        return q.resolve(P, null, List.of(new FieldText(MentionField.ANSWER, text)));
    }

    @Test
    void etatsDesMentions() {
        List<MentionState> found = states(queries, "voir @FAQ_Aurora_Features, @Suivi, @Inconnu, @ADR-2 et @ADR-3");
        assertThat(found).extracting(MentionState::text, MentionState::state).containsExactly(
                org.assertj.core.groups.Tuple.tuple("@FAQ_Aurora_Features", State.TRANSMITTED),
                org.assertj.core.groups.Tuple.tuple("@Suivi", State.AMBIGUOUS),
                org.assertj.core.groups.Tuple.tuple("@Inconnu", State.NOT_FOUND),
                org.assertj.core.groups.Tuple.tuple("@ADR-2", State.SUPERSEDED),
                org.assertj.core.groups.Tuple.tuple("@ADR-3", State.DEPRECATED));
        assertThat(found.get(0).transmittedShare()).isEqualTo(100);
        assertThat(found.get(1).candidates()).extracting(MentionQueries.Document::insert)
                .containsExactlyInAnyOrder("@Delivery/Suivi.xlsx", "@Archives/Suivi.xlsx");
        assertThat(found.get(3).document().path()).isEqualTo("aurora/adr/0001-decision-1.html");
        assertThat(states(queries, "sans mention")).isEmpty();
    }

    @Test
    void documentTropLongTransmisEnPartieEtPlusDePlace() {
        List<MentionState> found = states(queries, "@Aurora_Matrice_Regles_Gestion_Activites_1 @FAQ_Aurora_Features");
        assertThat(found.get(0).state()).isEqualTo(State.PARTIAL);
        assertThat(found.get(0).transmittedShare()).isBetween(20, 30);
        assertThat(found.get(1).state()).isEqualTo(State.TRANSMITTED);

        for (int i = 1; i <= 5; i++) {
            doc("Lot/D" + i + ".docx", "D" + i, 2_000);
        }
        List<MentionState> five = states(queries, "@D1 @D2 @D3 @D4 @D5");
        assertThat(five).extracting(MentionState::state).containsExactly(State.TRANSMITTED, State.TRANSMITTED,
                State.TRANSMITTED, State.TRANSMITTED, State.NO_ROOM);
    }

    @Test
    void laFicheGeneraleReduitLaPlace() {
        w.projects.save(w.projects.find(P).orElseThrow().withContext(ProjectContext.edited("x".repeat(20_000), T)));
        assertThat(states(queries, "@FAQ_Aurora_Features").getFirst().state()).isEqualTo(State.TRANSMITTED);
        doc("Lot/Gros.docx", "Gros", 9_000);
        assertThat(states(queries, "@Gros").getFirst().state()).isEqualTo(State.PARTIAL);
    }

    @Test
    void lesMentionsDeLaSessionPassentAvant() {
        Session session = Session.start(UUID.randomUUID(), P, Need.of("Le gestionnaire doit planifier ses activités du jour, voir @D1 et @D2."), null, T);
        Questionnaire q = Questionnaire.empty().addBatch(List.of(new ProposedQuestion(QuestionTheme.GOAL, "Objectif ?",
                "Pour cadrer", List.of(), null, List.of())), List.of(), Set.of(), UUID::randomUUID);
        q = q.answer(q.questions().getFirst().id(), "et aussi @D3 et @D4", T);
        w.sessions.save(session, q);
        for (int i = 1; i <= 5; i++) {
            doc("Lot/D" + i + ".docx", "D" + i, 2_000);
        }
        List<MentionState> found = queries.resolve(P, session.id(),
                List.of(new FieldText(MentionField.INSTRUCTION, "ajoute @D5")));
        assertThat(found).singleElement().satisfies(m -> assertThat(m.state()).isEqualTo(State.NO_ROOM));
        // un champ reçu remplace le besoin enregistré
        assertThat(queries.resolve(P, session.id(), List.of(new FieldText(MentionField.NEED, "@D5"))))
                .singleElement().satisfies(m -> assertThat(m.state()).isEqualTo(State.TRANSMITTED));
        assertThatThrownBy(() -> queries.resolve(P, UUID.randomUUID(), List.of())).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    // 008 : place selon le modèle choisi (FR-010) et modèle qui transmettrait tout (US3, research.md R5)

    static ModelCapacities models(Integer chosen, ModelCapacity... all) {
        return new ModelCapacities() {
            @Override
            public OptionalInt promptTokens() {
                return chosen == null ? OptionalInt.empty() : OptionalInt.of(chosen);
            }

            @Override
            public List<ModelCapacity> all() {
                return List.of(all);
            }
        };
    }

    static final ModelCapacity PETIT = new ModelCapacity("petit", "Petit", 32_000, null, 0.33);
    static final ModelCapacity MOYEN = new ModelCapacity("moyen", "Moyen", 64_000, null, 0.33);
    static final ModelCapacity GRAND = new ModelCapacity("grand", "Grand modèle", 128_000, 200_000, 1.0);
    static final ModelCapacity GRAND_BIS = new ModelCapacity("grand-bis", "Grand bis", 150_000, null, 1.0);
    static final ModelCapacity ENORME = new ModelCapacity("enorme", "Énorme", 1_000_000, null, 3.0);
    static final ModelCapacity SANS_COUT = new ModelCapacity("sans-cout", "Sans coût connu", 400_000, null, null);
    static final ModelCapacity SANS_LIMITE = new ModelCapacity("sans-limite", "Sans limite", null, null, 0.0);

    MentionQueries with(ModelCapacities capacities) {
        AnalyzeArchitecture analyze = new AnalyzeArchitecture(index, w.llm, w.policy);
        return new MentionQueries(w.projects, w.sessions, index,
                new SelectKnowledge(w.projects, index, new FakeEmbedding(), analyze, StoryFamily.none(), null, null,
                        capacities));
    }

    @Test
    void laPlaceSuitLeModeleChoisi() {
        String matrice = "@Aurora_Matrice_Regles_Gestion_Activites_1";
        assertThat(states(with(models(128_000, GRAND)), matrice).getFirst().state()).isEqualTo(State.TRANSMITTED);
        assertThat(states(with(models(128_000, GRAND)), matrice).getFirst().transmittedShare()).isEqualTo(100);
        assertThat(states(with(models(null, GRAND)), matrice).getFirst().state()).isEqualTo(State.PARTIAL);
        assertThat(states(with(models(32_000, PETIT)), matrice).getFirst().state()).isEqualTo(State.PARTIAL);
    }

    @Test
    void leModeleLeMoinsCouteuxQuiTransmetToutEstPropose() {
        ModelCapacities capacities = models(32_000, ENORME, SANS_COUT, GRAND_BIS, SANS_LIMITE, MOYEN, GRAND, PETIT);
        MentionState cut = states(with(capacities), "@Aurora_Matrice_Regles_Gestion_Activites_1").getFirst();

        assertThat(cut.state()).isEqualTo(State.PARTIAL);
        // moyen ne suffit pas ; grand et grand-bis coûtent autant : la plus petite limite l'emporte
        assertThat(cut.betterModel()).isEqualTo(new MentionQueries.BetterModel("grand", "Grand modèle", 1.0));
        // le modèle choisi n'est pas changé
        assertThat(capacities.promptTokens()).hasValue(32_000);
    }

    @Test
    void coutInconnuComptePourLePlusCher() {
        MentionState cut = states(with(models(32_000, SANS_COUT, ENORME)), "@Aurora_Matrice_Regles_Gestion_Activites_1")
                .getFirst();
        assertThat(cut.betterModel()).isEqualTo(new MentionQueries.BetterModel("enorme", "Énorme", 3.0));
        assertThat(states(with(models(32_000, SANS_COUT)), "@Aurora_Matrice_Regles_Gestion_Activites_1").getFirst()
                .betterModel()).isEqualTo(new MentionQueries.BetterModel("sans-cout", "Sans coût connu", null));
    }

    @Test
    void aucunModeleProposeSiRienNeSuffitOuSiToutPasse() {
        ModelCapacities capacities = models(32_000, GRAND, ENORME);
        doc("Lot/Immense.docx", "Immense", 60_000);
        assertThat(states(with(capacities), "@Immense").getFirst().state()).isEqualTo(State.PARTIAL);
        assertThat(states(with(capacities), "@Immense").getFirst().betterModel()).isNull();
        assertThat(states(with(capacities), "@FAQ_Aurora_Features").getFirst().betterModel()).isNull();
        // la cinquième mention n'a pas de place quel que soit le modèle : quatre documents au plus
        for (int i = 1; i <= 5; i++) {
            doc("Lot/D" + i + ".docx", "D" + i, 2_000);
        }
        assertThat(states(with(capacities), "@D1 @D2 @D3 @D4 @D5").get(4))
                .satisfies(m -> assertThat(m.state()).isEqualTo(State.NO_ROOM))
                .satisfies(m -> assertThat(m.betterModel()).isNull());
        // sans modèle connu, rien à proposer
        assertThat(states(queries, "@Aurora_Matrice_Regles_Gestion_Activites_1").getFirst().betterModel()).isNull();
    }

    @Test
    void plusDePlaceAvecUnGrandModele() {
        w.projects.save(w.projects.find(P).orElseThrow().withContext(ProjectContext.edited("x".repeat(20_000), T)));
        doc("Lot/Gros.docx", "Gros", 13_000);
        MentionState gros = states(with(models(32_000, GRAND)), "@FAQ_Aurora_Features @Gros").get(1);
        assertThat(gros.state()).isIn(State.PARTIAL, State.NO_ROOM);
        assertThat(gros.betterModel()).isEqualTo(new MentionQueries.BetterModel("grand", "Grand modèle", 1.0));
    }

    // 009 : état des documents joints, dans l'ordre de transmission (contracts/api.md)

    final InMemoryAttachments attachments = new InMemoryAttachments();
    final AttachmentDrafts drafts = new AttachmentDrafts(
            (name, content) -> AttachmentReader.Reading.of(null, new String(content, StandardCharsets.UTF_8)),
            new FixedClock(), new SequentialIdGenerator());

    MentionQueries withAttachments() {
        AnalyzeArchitecture analyze = new AnalyzeArchitecture(index, w.llm, w.policy);
        return new MentionQueries(w.projects, w.sessions, index,
                new SelectKnowledge(w.projects, index, new FakeEmbedding(), analyze, StoryFamily.none(), null, null,
                        ModelCapacities.UNKNOWN, attachments), drafts, attachments);
    }

    @Test
    void brouillonsAnnoncesCommeDesMentionsApresLeBesoin() {
        for (int i = 1; i <= 4; i++) {
            doc("Lot/D" + i + ".docx", "D" + i, 2_000);
        }
        AttachmentDraft j1 = drafts.create("CR COPIL.pdf", "c".repeat(1_000).getBytes(StandardCharsets.UTF_8));
        AttachmentDraft j2 = drafts.create("Annexe.docx", "a".repeat(1_000).getBytes(StandardCharsets.UTF_8));

        List<MentionState> found = withAttachments().resolve(P, null,
                List.of(new FieldText(MentionField.NEED, "voir @D1 et @D2"), new FieldText(MentionField.ANSWER, "@D3 @D4")),
                List.of(j1.id(), j2.id()));

        assertThat(found).extracting(MentionState::field).containsExactly(MentionField.NEED, MentionField.NEED,
                MentionField.ATTACHMENT, MentionField.ATTACHMENT, MentionField.ANSWER, MentionField.ANSWER);
        assertThat(found).extracting(MentionState::state).containsExactly(State.TRANSMITTED, State.TRANSMITTED,
                State.TRANSMITTED, State.TRANSMITTED, State.NO_ROOM, State.NO_ROOM);
        assertThat(found.get(2).document()).satisfies(d -> {
            assertThat(d.kind()).isEqualTo(MentionKind.ATTACHMENT);
            assertThat(d.title()).isEqualTo("CR COPIL");
            assertThat(d.path()).isEqualTo("CR COPIL.pdf");
            assertThat(d.insert()).isNull();
        });
        // sans aucune mention, les documents joints ont quand même leur état ; un brouillon expiré est ignoré
        assertThat(withAttachments().resolve(P, null, List.of(), List.of(j1.id(), UUID.randomUUID())))
                .singleElement().satisfies(m -> assertThat(m.transmittedShare()).isEqualTo(100));
    }

    @Test
    void documentsJointsDeLaSessionEtLongDocumentEnPartie() {
        Session session = Session.start(UUID.randomUUID(), P, Need.of("Le gestionnaire doit planifier ses activités du jour, en tenant compte des absences et des priorités."), null, T);
        w.sessions.save(session, Questionnaire.empty());
        attachments.add(Attachment.create(UUID.randomUUID(), session.id(), 1, null, "Long.pdf", "l".repeat(40_000), T));

        MentionState state = withAttachments().resolve(P, session.id(), List.of(), List.of()).getFirst();

        assertThat(state.field()).isEqualTo(MentionField.ATTACHMENT);
        assertThat(state.state()).isEqualTo(State.PARTIAL);
        assertThat(state.transmittedShare()).isBetween(1, 99);
        assertThat(state.text()).isEqualTo("Long");
    }
}
