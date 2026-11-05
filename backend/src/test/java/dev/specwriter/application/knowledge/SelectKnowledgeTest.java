package dev.specwriter.application.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.jira.TicketLink;
import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.ArchElement;
import dev.specwriter.domain.knowledge.ArchScope;
import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeRole;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.llm.ImpactResult;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import dev.specwriter.support.FakeEmbedding;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import dev.specwriter.support.JiraWorld;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T109 : sélection de la connaissance transmise à l'IA pour une session (research.md R24). */
class SelectKnowledgeTest {

    static final UUID P = Project.DEFAULT_ID;
    static final Instant T = Instant.parse("2026-10-01T08:00:00Z");
    static final Instant SHEET_AT = Instant.parse("2026-11-02T10:00:00Z");
    static final String NEED = "Le gestionnaire doit pouvoir annuler une facture émise par un avoir total.";
    static final String SHEET = "## Acteurs\n- Le gestionnaire de facturation émet les avoirs.";

    final JiraWorld w = new JiraWorld().grant(ConsentService.COPILOT);
    final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    final FakeEmbedding embedding = new FakeEmbedding();
    SelectKnowledge select;
    Session session;

    static String text(String start, int length) {
        StringBuilder out = new StringBuilder(start);
        while (out.length() < length) {
            out.append(" facture avoir");
        }
        return out.substring(0, length);
    }

    static KnowledgeItem adr(int number, AdrStatus status, String supersededBy) {
        String ref = "facturation/adr/adr-%03d.html".formatted(number);
        return KnowledgeItem.adr(P, ref, 0, "ADR-%03d Avoirs et factures".formatted(number),
                text("Décision " + number + " sur l'avoir et la facture.", 2_500),
                AdrStatus.Statement.of(status, supersededBy), T, T);
    }

    static QuestionAndAnswer answer(String id, String value) {
        return new QuestionAndAnswer(id, QuestionTheme.GOAL, "Objectif ?", value, false);
    }

    void sheet(String markdown) {
        w.projects.save(w.projects.find(P).orElseThrow().withContext(ProjectContext.edited(markdown, SHEET_AT)));
    }

    @BeforeEach
    void setUp() {
        embedding.state = EmbeddingPort.State.READY;
        index.save(KnowledgeItem.docSection(P, "facturation/glossaire.html", 0, "Glossaire",
                text("Avoir : document qui annule tout ou partie d'une facture.", 2_500), KnowledgeRole.VOCABULARY, T, T),
                null, null);
        index.save(KnowledgeItem.docSection(P, "facturation/architecture.html", 0, "Vue d'ensemble",
                text("La facturation publie ses événements.", 2_500), KnowledgeRole.ARCHITECTURE_OVERVIEW, T, T), null,
                null);
        index.save(KnowledgeItem.docSection(P, "facturation/regles/avoir.html", 0, "Règles de l'avoir",
                text("Un avoir ne dépasse pas le restant dû de la facture.", 2_000), null, T, T), null, null);
        index.save(adr(1, AdrStatus.ACCEPTED, null), null, null);
        index.save(adr(2, AdrStatus.SUPERSEDED, "ADR-005"), null, null);
        index.save(adr(3, AdrStatus.DEPRECATED, null), null, null);
        index.save(adr(4, AdrStatus.ACCEPTED, null), null, null);
        index.save(adr(5, AdrStatus.ACCEPTED, null), null, null);
        index.save(adr(6, AdrStatus.PROPOSED, null), null, null);
        index.save(adr(7, AdrStatus.ACCEPTED, null), null, null);
        for (int i = 1; i <= 5; i++) {
            index.save(KnowledgeItem.ticket(P, "FACT-" + i, "Avoir sur facture " + i,
                    text("Description de l'avoir " + i + " sur une facture.", 1_800), "À faire", i == 1 ? "done" : "new",
                    "Story", T, T), null, null);
        }
        index.save(KnowledgeItem.archElement(P, new ArchElement("Facturation/Service Avoirs", ArchElement.Type.CONTAINER,
                ArchScope.PROJECT, false, "Émet les avoirs sur facture", "Java"), T, T), null, null);
        index.save(KnowledgeItem.archElement(P, new ArchElement("Comptabilité", ArchElement.Type.SOFTWARE_SYSTEM,
                ArchScope.COMMON, false, "Reçoit les avoirs et factures", null), T, T), null, null);
        index.replaceArchitecture(P, ArchitectureModel.EMPTY);
        AnalyzeArchitecture analyze = new AnalyzeArchitecture(index, w.llm, w.policy);
        select = new SelectKnowledge(w.projects, index, embedding, analyze);
        session = Session.start(UUID.randomUUID(), P, Need.of(NEED), null, w.clock.now());
    }

    static int size(ProjectKnowledge k, KnowledgeKind kind) {
        return k.entries().stream().filter(e -> e.kind() == kind).mapToInt(e -> e.title().length() + e.text().length())
                .sum();
    }

    static int total(ProjectKnowledge k) {
        return k.generalSheet().length() + k.entries().stream().mapToInt(e -> e.title().length() + e.text().length()).sum();
    }

    @Test
    void ficheGeneraleToujoursTransmise() {
        sheet(SHEET);
        ProjectKnowledge k = select.forQuestions(session, List.of());
        assertThat(k.generalSheet()).isEqualTo(SHEET);
        assertThat(k.sheetUpdatedAt()).isEqualTo(SHEET_AT);

        // sans fiche ni index : la connaissance est vide, et le message celui de 001
        SelectKnowledge none = new SelectKnowledge(new JiraWorld().projects, new InMemoryKnowledgeIndex(), embedding,
                new AnalyzeArchitecture(new InMemoryKnowledgeIndex(), w.llm, w.policy));
        assertThat(none.forQuestions(session, List.of()).isEmpty()).isTrue();
    }

    @Test
    void vocabulaireEtVueDEnsembleBornesATroisMilleCaracteres() {
        ProjectKnowledge k = select.forQuestions(session, List.of());
        List<ProjectKnowledge.Entry> roles = k.entries().stream().filter(e -> e.role() != null).toList();
        assertThat(roles).extracting(ProjectKnowledge.Entry::role).first().isNotNull();
        assertThat(roles.stream().mapToInt(e -> e.title().length() + e.text().length()).sum()).isLessThanOrEqualTo(3_000);
        assertThat(roles.getFirst().ref()).isIn("facturation/glossaire.html", "facturation/architecture.html");
    }

    @Test
    void quatreAdrAuPlusEtUnAdrRemplaceParSonSuccesseur() {
        ProjectKnowledge k = select.forQuestions(session, List.of());
        List<ProjectKnowledge.Entry> adrs = k.entries().stream().filter(e -> e.kind() == KnowledgeKind.ADR).toList();
        assertThat(adrs).hasSize(4);
        assertThat(size(k, KnowledgeKind.ADR)).isLessThanOrEqualTo(6_000);
        assertThat(adrs).extracting(ProjectKnowledge.Entry::ref)
                .doesNotContain("facturation/adr/adr-002.html", "facturation/adr/adr-003.html")
                .contains("facturation/adr/adr-005.html").doesNotHaveDuplicates();
        assertThat(adrs).extracting(ProjectKnowledge.Entry::status).allMatch(s -> s.equals("ACCEPTED")
                || s.equals("PROPOSED"));
    }

    /** Convention adr-tools : le successeur « ADR-0005 » est le fichier 0005-… ou le titre « 5. … ». */
    @Test
    void successeurRetrouveParLeNumeroDeSonFichier() {
        index.items.get(P).keySet().removeIf(k -> k.kind() == KnowledgeKind.ADR);
        index.save(KnowledgeItem.adr(P, "projet/adr/0002-avoir-synchrone.html", 0, "2. Avoir synchrone",
                text("Décision sur l'avoir et la facture.", 1_000), AdrStatus.Statement.of(AdrStatus.SUPERSEDED,
                        "ADR-0005"), T, T), null, null);
        // sans aucun terme commun avec le besoin : retenu seulement comme successeur
        index.save(KnowledgeItem.adr(P, "projet/adr/0005-zz.html", 0, "5. Zz", "Qq.",
                AdrStatus.Statement.of(AdrStatus.ACCEPTED, null), T, T), null, null);
        List<ProjectKnowledge.Entry> adrs = select.forQuestions(session, List.of()).entries().stream()
                .filter(e -> e.kind() == KnowledgeKind.ADR).toList();
        assertThat(adrs).extracting(ProjectKnowledge.Entry::ref).containsExactly("projet/adr/0005-zz.html");
    }

    @Test
    void troisTicketsRetenus() {
        ProjectKnowledge k = select.forQuestions(session, List.of());
        List<ProjectKnowledge.Entry> tickets = k.entries().stream().filter(e -> e.kind() == KnowledgeKind.TICKET)
                .toList();
        assertThat(tickets).hasSize(3);
        assertThat(size(k, KnowledgeKind.TICKET)).isLessThanOrEqualTo(3_000);
        assertThat(tickets).filteredOn(e -> e.ref().equals("FACT-1")).allMatch(ProjectKnowledge.Entry::closed);
        assertThat(k.entries()).noneMatch(e -> e.ref().equals("facturation/regles/avoir.html"));
    }

    @Test
    void trenteCinqMilleCaracteresAuPlusEnTout() {
        sheet(text("## Fiche générale", 20_000));
        w.llm.impacts.add(new ImpactResult(List.of("Facturation/Service Avoirs", "Comptabilité"),
                List.of(new ImpactResult.Exchange("Facturation/Service Avoirs", "Comptabilité", "Transmettre l'avoir"))));
        ProjectKnowledge k = select.forSpecification(session, List.of());
        assertThat(k.generalSheet()).hasSize(ProjectKnowledge.MAX_SHEET);
        assertThat(total(k)).isLessThanOrEqualTo(ProjectKnowledge.MAX_TOTAL - SelectKnowledge.MAX_ARCHITECTURE);
        assertThat(SelectKnowledge.architectureSize(k)).isLessThanOrEqualTo(SelectKnowledge.MAX_ARCHITECTURE);
    }

    @Test
    void faitsDArchitecturePourLaSpecificationSeulement() {
        w.llm.impacts.add(new ImpactResult(List.of("Facturation/Service Avoirs", "Comptabilité"),
                List.of(new ImpactResult.Exchange("Facturation/Service Avoirs", "Comptabilité", "Transmettre l'avoir"))));
        ProjectKnowledge questions = select.forQuestions(session, List.of());
        assertThat(questions.hasArchitecture()).isFalse();
        assertThat(w.llm.impactRequests).isEmpty();

        ProjectKnowledge spec = select.forSpecification(session, List.of());
        assertThat(spec.modules()).extracting(m -> m.name() + ":" + m.scope())
                .containsExactly("Facturation/Service Avoirs:PROJECT", "Comptabilité:COMMON");
        assertThat(spec.exchanges()).singleElement()
                .extracting(ArchitectureModel.Exchange::kind).isEqualTo(ArchitectureModel.Exchange.Kind.MISSING);
        assertThat(spec.selection().modules()).hasSize(2);
        assertThat(spec.selection().items()).extracting(s -> s.contentHash()).doesNotContainNull();
    }

    @Test
    void recalculeQuandLeBesoinOuLesReponsesChangent() {
        for (int i = 0; i < 3; i++) {
            w.llm.impacts.add(new ImpactResult(List.of("Comptabilité"), List.of()));
        }
        List<QuestionAndAnswer> answers = List.of(answer("q1", "Un avoir total"));
        select.forSpecification(session, answers);
        select.forSpecification(session, answers);
        assertThat(w.llm.impactRequests).hasSize(1);

        select.forSpecification(session, List.of(answer("q1", "Un avoir partiel")));
        assertThat(w.llm.impactRequests).hasSize(2);

        Session other = new Session(session.id(), session.projectId(), session.name(),
                Need.of(NEED + " Le client est prévenu."), session.language(), session.origin(), session.state(),
                session.createdAt(), session.updatedAt());
        select.forSpecification(other, List.of(answer("q1", "Un avoir partiel")));
        assertThat(w.llm.impactRequests).hasSize(3);
    }

    @Test
    void echecDeLAnalyseNeBloquePasEtNEstPasRetenu() {
        // aucune réponse préparée : l'appel échoue, la connaissance reste sans architecture
        ProjectKnowledge k = select.forSpecification(session, List.of());
        assertThat(k.hasArchitecture()).isFalse();
        w.llm.impacts.add(new ImpactResult(List.of("Comptabilité"), List.of()));
        assertThat(select.forSpecification(session, List.of()).modules()).hasSize(1);
    }

    /** T124 : la famille de la story liée n'est jamais un ticket concurrent ; ses sous-tâches sont à part (R29). */
    @Test
    void famillesDeLaStoryExclueEtSousTachesTransmisesAPart() {
        w.link(session, "FACT-1", "d");
        w.ticketLinks.replace(P, "FACT-1", List.of(TicketLink.parent("FACT-9"), TicketLink.subtask("FACT-2"),
                TicketLink.subtask("FACT-3")));
        w.ticketLinks.replace(P, "FACT-4", List.of(TicketLink.link("Blocks", TicketLink.Direction.OUTWARD, "blocks",
                "FACT-1")));
        SelectKnowledge withFamily = new SelectKnowledge(w.projects, index, embedding,
                new AnalyzeArchitecture(index, w.llm, w.policy), new StoryFamily(w.tickets, w.ticketLinks));

        ProjectKnowledge k = withFamily.forQuestions(session, List.of());
        List<ProjectKnowledge.Entry> tickets = k.entries().stream().filter(e -> e.kind() == KnowledgeKind.TICKET).toList();
        assertThat(tickets).extracting(ProjectKnowledge.Entry::ref).doesNotContain("FACT-1", "FACT-2", "FACT-3", "FACT-9")
                .containsExactlyInAnyOrder("FACT-4", "FACT-5");
        assertThat(tickets).filteredOn(e -> e.ref().equals("FACT-4")).singleElement()
                .extracting(ProjectKnowledge.Entry::link).isEqualTo("FACT-4 blocks FACT-1");
        assertThat(k.subtasks().storyKey()).isEqualTo("FACT-1");
        assertThat(k.subtasks().items()).extracting(ProjectKnowledge.Subtask::ref, ProjectKnowledge.Subtask::title)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("FACT-2", "Avoir sur facture 2"),
                        org.assertj.core.groups.Tuple.tuple("FACT-3", "Avoir sur facture 3"));

        // le statut d'une sous-tâche entre dans l'empreinte (FR-011)
        String before = k.fingerprint();
        index.save(KnowledgeItem.ticket(P, "FACT-2", "Avoir sur facture 2",
                text("Description de l'avoir 2 sur une facture.", 1_800), "Terminé", "done", "Story", T, T), null, null);
        assertThat(withFamily.forQuestions(session, List.of()).fingerprint()).isNotEqualTo(before);
    }

    @Test
    void quinzeSousTachesAuPlusEtMilleCinqCentsCaracteres() {
        w.link(session, "FACT-1", "d");
        List<TicketLink> many = new java.util.ArrayList<>();
        for (int i = 100; i < 130; i++) {
            index.save(KnowledgeItem.ticket(P, "FACT-" + i, text("Sous-tâche " + i, 150), "", "À faire", "new",
                    "Sous-tâche", T, T), null, null);
            many.add(TicketLink.subtask("FACT-" + i));
        }
        w.ticketLinks.replace(P, "FACT-1", many);
        ProjectKnowledge k = new SelectKnowledge(w.projects, index, embedding,
                new AnalyzeArchitecture(index, w.llm, w.policy), new StoryFamily(w.tickets, w.ticketLinks))
                .forQuestions(session, List.of());
        assertThat(k.subtasks().items()).hasSizeLessThanOrEqualTo(15);
        assertThat(k.subtasks().items().stream().mapToInt(t -> t.title().length()).sum()).isLessThanOrEqualTo(1_500);
    }

    /** Une story qui est elle-même une sous-tâche : son parent est de la famille, pas ses sœurs. */
    @Test
    void storySousTacheSonParentEstDeLaFamille() {
        w.ticketLinks.replace(P, "FACT-1", List.of(TicketLink.subtask("FACT-2"), TicketLink.subtask("FACT-3")));
        w.ticketLinks.replace(P, "FACT-2", List.of(TicketLink.parent("FACT-1")));
        StoryFamily.Family family = new StoryFamily(w.tickets, w.ticketLinks).of(P, "FACT-2");
        assertThat(family.parentKey()).isEqualTo("FACT-1");
        assertThat(family.contains("FACT-1")).isTrue();
        assertThat(family.contains("FACT-3")).isFalse();
        assertThat(new StoryFamily(w.tickets, w.ticketLinks).parentOf(P, "FACT-3")).contains("FACT-1");
    }
}
