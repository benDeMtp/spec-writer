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
import dev.specwriter.domain.port.DocumentFolder;
import dev.specwriter.domain.port.DocumentationReader.Page;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.ExclusionPatterns;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import dev.specwriter.domain.question.QuestionTheme;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import dev.specwriter.support.FakeDocumentFolder;
import dev.specwriter.support.FakeEmbedding;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import dev.specwriter.support.JiraWorld;
import java.time.Instant;
import java.util.ArrayList;
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
    AnalyzeArchitecture analyze;
    Session session;
    final DocSource documents = new DocSource(UUID.fromString("00000000-0000-0000-0000-000000000006"), P,
            DocSource.Kind.DOCUMENTS, "/sync/Aurora", null, null, ExclusionPatterns.NONE);

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
        analyze = new AnalyzeArchitecture(index, w.llm, w.policy);
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

    /** Des ADR de 2 500 caractères passent entiers : quatre tiennent dans les 12 000 (004, R4 et FR-017). */
    @Test
    void adrEntiersEtUnAdrRemplaceParSonSuccesseur() {
        ProjectKnowledge k = select.forQuestions(session, List.of());
        List<ProjectKnowledge.Entry> adrs = k.entries().stream().filter(e -> e.kind() == KnowledgeKind.ADR).toList();
        assertThat(adrs).hasSize(4).allSatisfy(e -> assertThat(e.text()).hasSize(2_500));
        assertThat(size(k, KnowledgeKind.ADR)).isLessThanOrEqualTo(12_000);
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

    // ---------- 004, US3 : l'ADR entier ----------

    void onlyAdrs() {
        index.items.get(P).keySet().removeIf(k -> k.kind() == KnowledgeKind.ADR);
    }

    void adrChunk(String ref, int chunk, String title, String body, AdrStatus status, String supersededBy) {
        index.save(KnowledgeItem.adr(P, ref, chunk, title, body, AdrStatus.Statement.of(status, supersededBy), T, T),
                null, null);
    }

    List<ProjectKnowledge.Entry> adrEntries() {
        return select.forQuestions(session, List.of()).entries().stream().filter(e -> e.kind() == KnowledgeKind.ADR)
                .toList();
    }

    /** US3-1, US3-2 : un morceau retenu fait transmettre l'ADR entier, une fois, morceaux dans l'ordre. */
    @Test
    void morceauRetenuAdrEntierUneSeuleFois() {
        onlyAdrs();
        adrChunk("adr/0017-bff.html", 0, "17. BFF", "## Contexte\n\nZz qq.", AdrStatus.ACCEPTED, null);
        adrChunk("adr/0017-bff.html", 1, "17. BFF", "## Décision\n\nWw kk.", AdrStatus.ACCEPTED, null);
        adrChunk("adr/0017-bff.html", 2, "17. BFF", "## Conséquences\n\nUne facture annulée par un avoir total.",
                AdrStatus.ACCEPTED, null);
        adrChunk("adr/0017-bff.html", 3, "17. BFF", "Le gestionnaire émet l'avoir.", AdrStatus.ACCEPTED, null);
        assertThat(adrEntries()).singleElement().satisfies(e -> {
            assertThat(e.ref()).isEqualTo("adr/0017-bff.html");
            assertThat(e.chunk()).isZero();
            assertThat(e.text()).isEqualTo("## Contexte\n\nZz qq.\n\n## Décision\n\nWw kk.\n\n## Conséquences\n\n"
                    + "Une facture annulée par un avoir total.\n\nLe gestionnaire émet l'avoir.");
        });
    }

    /** US3-3 : les ADR passent entiers tant que le total le permet ; le premier qui ne tient pas arrête la liste. */
    @Test
    void adrEntiersDansLeBudget() {
        onlyAdrs();
        adrChunk("adr/0001-a.html", 0, "1. A", text("Avoir et facture A.", 6_000), AdrStatus.ACCEPTED, null);
        adrChunk("adr/0002-b.html", 0, "2. B", text("Avoir et facture B.", 5_000), AdrStatus.ACCEPTED, null);
        adrChunk("adr/0003-c.html", 0, "3. C", text("Avoir et facture C.", 4_000), AdrStatus.ACCEPTED, null);
        List<ProjectKnowledge.Entry> adrs = adrEntries();
        assertThat(adrs).extracting(ProjectKnowledge.Entry::ref).containsExactly("adr/0001-a.html", "adr/0002-b.html");
        assertThat(adrs).allSatisfy(e -> assertThat(e.text()).doesNotContain("…").doesNotContain("[ADR tronqué]"));
        assertThat(adrs.get(0).text()).hasSize(6_000);
    }

    /** US3-3 : un ADR plus long que sa part est coupé à la dernière section complète, et le dit. */
    @Test
    void adrTropLongCoupeAUneSection() {
        onlyAdrs();
        // un élément indexé fait 8 000 caractères au plus : l'ADR long est en deux morceaux
        adrChunk("adr/0004-long.html", 0, "4. Long", "## Contexte\n\n" + text("Avoir et facture.", 3_000)
                + "\n\n## Décision\n\n" + "d".repeat(3_000), AdrStatus.ACCEPTED, null);
        adrChunk("adr/0004-long.html", 1, "4. Long", "## Conséquences\n\n" + "c".repeat(3_000), AdrStatus.ACCEPTED,
                null);
        String sent = adrEntries().getFirst().text();
        assertThat(sent).endsWith("d".repeat(3_000) + "\n\n[ADR tronqué]").doesNotContain("Conséquences");
        assertThat(sent.length()).isLessThanOrEqualTo(SelectKnowledge.MAX_ADR_PART);

        onlyAdrs();
        adrChunk("adr/0005-plat.html", 0, "5. Plat", text("Avoir et facture.", 3_900), AdrStatus.ACCEPTED, null);
        adrChunk("adr/0005-plat.html", 1, "5. Plat", "e".repeat(3_900), AdrStatus.ACCEPTED, null);
        adrChunk("adr/0005-plat.html", 2, "5. Plat", "f".repeat(3_900), AdrStatus.ACCEPTED, null);
        assertThat(adrEntries().getFirst().text()).endsWith("e".repeat(3_900) + "\n\n[ADR tronqué]");
    }

    /** US3-4 : un ADR remplacé retenu cède la place à son successeur, transmis entier. */
    @Test
    void successeurTransmisEntier() {
        onlyAdrs();
        adrChunk("adr/0002-ancien.html", 0, "2. Ancien", "Avoir et facture, version synchrone.", AdrStatus.SUPERSEDED,
                "ADR-0006");
        adrChunk("adr/0006-nouveau.html", 0, "6. Nouveau", "Partie un.", AdrStatus.ACCEPTED, null);
        adrChunk("adr/0006-nouveau.html", 1, "6. Nouveau", "Partie deux.", AdrStatus.ACCEPTED, null);
        assertThat(adrEntries()).singleElement().satisfies(e -> {
            assertThat(e.ref()).isEqualTo("adr/0006-nouveau.html");
            assertThat(e.text()).isEqualTo("Partie un.\n\nPartie deux.");
        });
    }

    /** US3-5 et quatre ADR au plus. */
    @Test
    void reproductibleEtQuatreAdrAuPlus() {
        onlyAdrs();
        for (int i = 1; i <= 6; i++) {
            adrChunk("adr/000" + i + "-x.html", 0, i + ". X", "Avoir et facture " + i + ".", AdrStatus.ACCEPTED, null);
        }
        List<ProjectKnowledge.Entry> first = adrEntries();
        assertThat(first).hasSize(4);
        assertThat(adrEntries()).isEqualTo(first);
    }

    // ---------- 004, US4 : les ADR cités ----------

    List<ProjectKnowledge.Entry> editAdrs(String instruction, String markdown) {
        return select.forEdit(session, List.of(), instruction, markdown).entries().stream()
                .filter(e -> e.kind() == KnowledgeKind.ADR).toList();
    }

    /** US4-1 : un ADR cité dans l'instruction, sans terme commun avec le besoin, passe en premier. */
    @Test
    void adrCiteDansLInstructionTransmisEnPremier() {
        adrChunk("adr/0017-zz.html", 0, "17. Zz", "Qq.", AdrStatus.PROPOSED, null);
        List<ProjectKnowledge.Entry> adrs = editAdrs("Ajoute la configuration nginx demandée par l'ADR 0017.", "");
        assertThat(adrs.getFirst().ref()).isEqualTo("adr/0017-zz.html");
        assertThat(adrs.getFirst().text()).isEqualTo("Qq.");
        assertThat(select.forQuestions(session, List.of()).entries()).extracting(ProjectKnowledge.Entry::ref)
                .doesNotContain("adr/0017-zz.html");
    }

    /** US4-2 : deux ADR de même numéro, l'un cité : les deux passent. */
    @Test
    void deuxAdrDeMemeNumeroCitesTousLesDeux() {
        onlyAdrs();
        adrChunk("adr/0017-bff.html", 0, "17. BFF", "Rr.", AdrStatus.PROPOSED, null);
        adrChunk("adr/0017-nginx.html", 0, "17. Nginx", "Ss.", AdrStatus.PROPOSED, null);
        assertThat(editAdrs("Voir l'ADR-17.", "")).extracting(ProjectKnowledge.Entry::ref)
                .containsExactlyInAnyOrder("adr/0017-bff.html", "adr/0017-nginx.html");
    }

    /** US4-3 : citation dans la version en cours, dans une réponse, dans le besoin. */
    @Test
    void citationDansLaVersionLesReponsesOuLeBesoin() {
        onlyAdrs();
        adrChunk("adr/0021-yy.html", 0, "21. Yy", "Tt.", AdrStatus.ACCEPTED, null);
        assertThat(editAdrs("Reformule.", "Conformément à l'ADR n°21, …")).extracting(ProjectKnowledge.Entry::ref)
                .containsExactly("adr/0021-yy.html");
        assertThat(select.forQuestions(session, List.of(answer("q1", "Comme le dit l'ADR 21.")))
                .entries()).extracting(ProjectKnowledge.Entry::ref).contains("adr/0021-yy.html");
        Session citing = Session.start(UUID.randomUUID(), P, Need.of(NEED + " Voir ADR-21."), null, w.clock.now());
        assertThat(select.forQuestions(citing, List.of()).entries()).extracting(ProjectKnowledge.Entry::ref)
                .contains("adr/0021-yy.html");
    }

    /** US4-4 : un ADR cité remplacé cède la place à son successeur. */
    @Test
    void adrCiteRemplaceParSonSuccesseur() {
        onlyAdrs();
        adrChunk("adr/0030-vieux.html", 0, "30. Vieux", "Uu.", AdrStatus.SUPERSEDED, "ADR-0031");
        adrChunk("adr/0031-neuf.html", 0, "31. Neuf", "Vv.", AdrStatus.ACCEPTED, null);
        assertThat(editAdrs("Selon l'ADR 30.", "")).extracting(ProjectKnowledge.Entry::ref)
                .containsExactly("adr/0031-neuf.html");
    }

    /** Fiche générale au maximum : les ADR prennent ce qui reste, et l'ADR cité passe coupé plutôt que pas du tout. */
    @Test
    void ficheGeneraleMaximaleAdrCiteCoupe() {
        sheet(text("## Fiche générale", 20_000));
        onlyAdrs();
        adrChunk("adr/0017-nginx.html", 0, "17. Nginx", "## Contexte\n\n" + "x".repeat(3_700)
                + "\n\n## Décision\n\n" + "y".repeat(3_850), AdrStatus.PROPOSED, null);
        ProjectKnowledge k = select.forEdit(session, List.of(), "Configuration nginx de l'ADR 0017.", "");
        ProjectKnowledge.Entry adr = k.entries().stream().filter(e -> e.kind() == KnowledgeKind.ADR).findFirst()
                .orElseThrow();
        assertThat(adr.text()).startsWith("## Contexte").endsWith("[ADR tronqué]");
        assertThat(total(k)).isLessThanOrEqualTo(ProjectKnowledge.MAX_TOTAL - SelectKnowledge.MAX_ARCHITECTURE);
    }

    /** FR-017 : un ADR de 7 600 caractères passe entier (8 000 par ADR, 12 000 au total). */
    @Test
    void adrLongCiteTransmisEntier() {
        onlyAdrs();
        String body = "## Contexte\n\n" + "x".repeat(3_700) + "\n\n## Décision\n\n" + "y".repeat(3_850);
        adrChunk("adr/0017-nginx.html", 0, "17. Nginx", body, AdrStatus.PROPOSED, null);
        assertThat(editAdrs("Configuration nginx de l'ADR 0017.", "").getFirst().text()).isEqualTo(body);
    }

    /** US4-5 : la recherche d'une modification porte aussi sur l'instruction. */
    @Test
    void laRechercheDUneModificationPorteSurLInstruction() {
        onlyAdrs();
        adrChunk("adr/0040-k8s.html", 0, "40. Kubernetes", "Déploiement kubernetes des services.", AdrStatus.ACCEPTED,
                null);
        assertThat(select.forQuestions(session, List.of()).entries()).extracting(ProjectKnowledge.Entry::ref)
                .doesNotContain("adr/0040-k8s.html");
        assertThat(editAdrs("Précise le déploiement kubernetes.", "")).extracting(ProjectKnowledge.Entry::ref)
                .containsExactly("adr/0040-k8s.html");
    }

    /**
     * Essai de l'utilisateur : « vérifie s'il y a un ticket qui traite d'OIDC ». Le ticket OIDC, que
     * seule l'instruction désigne, passe malgré les tickets plus proches du besoin.
     */
    @Test
    void ticketDesigneParLInstructionSeule() {
        index.save(KnowledgeItem.ticket(P, "AURORA-23", "Authentification OIDC avec GAIA", "Client OIDC.", "Créée",
                "new", "Story", T, T), null, null);
        List<String> plain = select.forSpecification(session, List.of()).entries().stream()
                .filter(e -> e.kind() == KnowledgeKind.TICKET).map(ProjectKnowledge.Entry::ref).toList();
        assertThat(plain).hasSize(3).doesNotContain("AURORA-23");
        List<String> edited = select.forEdit(session, List.of(),
                "Vérifie s'il y a un ticket qui traite d'OIDC et fais-y référence.", "").entries().stream()
                .filter(e -> e.kind() == KnowledgeKind.TICKET).map(ProjectKnowledge.Entry::ref).toList();
        assertThat(edited).hasSize(3).contains("AURORA-23");
    }

    /**
     * Fusion par rangs réciproques (RRF) : un élément trouvé par les deux recherches passe devant le
     * premier d'une seule ; à égalité, l'ordre des recherches départage.
     */
    @Test
    void fusionDesRecherchesParRangs() {
        KnowledgeItem a = ticket("A");
        KnowledgeItem b = ticket("B");
        KnowledgeItem c = ticket("C");
        KnowledgeItem d = ticket("D");
        KnowledgeItem e = ticket("E");
        assertThat(SelectKnowledge.fuse(List.of(List.of(a, b, c), List.of(c, d, e))))
                .extracting(KnowledgeItem::ref).containsExactly("C", "A", "B", "D", "E");
        assertThat(SelectKnowledge.fuse(List.of(List.of(a, b)))).containsExactly(a, b);
    }

    private static KnowledgeItem ticket(String key) {
        return KnowledgeItem.ticket(P, key, "Ticket " + key, "", "Créée", "new", "Story", T, T);
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
    // 006, US3 : documents d'un dossier synchronisé relus entiers pour la génération (research.md R6)

    SelectKnowledge withDocuments() {
        w.docSources.save(documents);
        return new SelectKnowledge(w.projects, index, embedding, analyze, StoryFamily.none(), w.docSources, w.folder);
    }

    /** Un document indexé par parties ; s'il est sur le poste, sa relecture rend le texte entier. */
    String document(String path, String title, List<String> parts, int partLength, boolean onDisk) {
        String ref = DocumentFolder.ref(documents, path);
        List<String> text = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            String body = text(parts.get(i) + " : l'avoir de la facture.", partLength);
            index.save(KnowledgeItem.docSection(P, ref + "#" + i, 0, title + " — " + parts.get(i), body, null, T, T),
                    null, null);
            text.add("# " + parts.get(i) + "\n\n" + body);
        }
        if (onDisk) {
            w.folder.put(documents.location(), path, 1_000, T,
                    FakeDocumentFolder.page(new Page(ref, title, String.join("\n\n", text), List.of())));
        }
        return ref;
    }

    static List<ProjectKnowledge.Entry> documents(ProjectKnowledge k) {
        return k.entries().stream().filter(e -> e.ref().startsWith("docs:")).toList();
    }

    @Test
    void documentRetenuReluEntier() {
        String ref = document("Specs/Avoirs.docx", "Avoirs", List.of("Contexte", "Exigences"), 1_000, true);

        ProjectKnowledge k = withDocuments().forSpecification(session, List.of());

        assertThat(documents(k)).singleElement().satisfies(e -> {
            assertThat(e.kind()).isEqualTo(KnowledgeKind.DOC_SECTION);
            assertThat(e.ref()).isEqualTo(ref);
            assertThat(e.title()).isEqualTo("Avoirs");
            assertThat(e.text()).startsWith("# Contexte\n\nContexte").contains("\n\n# Exigences\n\nExigences")
                    .doesNotContain("[document tronqué]");
        });
        assertThat(w.folder.reads).containsExactly("Specs/Avoirs.docx");
        // les sections de documentation ordinaires ne vont toujours pas à la génération
        assertThat(k.entries()).extracting(ProjectKnowledge.Entry::title).doesNotContain("Règles de l'avoir");
        assertThat(total(k)).isLessThanOrEqualTo(ProjectKnowledge.MAX_TOTAL);
    }

    @Test
    void deuxDocumentsAuPlusCoupesAUneSection() {
        // le document A, le plus proche du besoin, vient en premier
        document("A.docx", "Avoirs A", List.of("Un gestionnaire annuler", "Deux gestionnaire annuler",
                "Trois gestionnaire annuler", "Quatre gestionnaire annuler"), 2_000, true);
        document("B.docx", "Avoirs B", List.of("Un"), 1_000, true);
        document("C.docx", "Avoirs C", List.of("Un"), 1_000, true);

        ProjectKnowledge k = withDocuments().forSpecification(session, List.of());

        List<ProjectKnowledge.Entry> docs = documents(k);
        assertThat(docs).hasSize(SelectKnowledge.MAX_DOCUMENTS);
        ProjectKnowledge.Entry first = docs.getFirst();
        assertThat(first.title().length() + first.text().length()).isLessThanOrEqualTo(SelectKnowledge.MAX_DOCUMENT_PART);
        assertThat(first.text()).endsWith("\n\n[document tronqué]").contains("# Deux").doesNotContain("# Quatre");
        assertThat(docs.stream().mapToInt(e -> e.title().length() + e.text().length()).sum())
                .isLessThanOrEqualTo(SelectKnowledge.MAX_DOCUMENT_TEXT);
    }

    @Test
    void laFicheEtLesAdrPassentAvantLesDocuments() {
        sheet(text("## Fiche générale", 20_000));
        document("Specs/Avoirs.docx", "Avoirs", List.of("Contexte", "Exigences"), 1_500, true);

        ProjectKnowledge k = withDocuments().forSpecification(session, List.of());

        assertThat(size(k, KnowledgeKind.ADR)).isPositive();
        assertThat(documents(k)).singleElement()
                .satisfies(e -> assertThat(e.text()).contains("# Contexte").doesNotContain("# Exigences")
                        .endsWith("[document tronqué]"));
        assertThat(total(k)).isLessThanOrEqualTo(ProjectKnowledge.MAX_TOTAL - SelectKnowledge.MAX_ARCHITECTURE
                - SelectKnowledge.MAX_TICKET_TEXT - SelectKnowledge.MAX_SUBTASK_TEXT + size(k, KnowledgeKind.TICKET));
    }

    @Test
    void documentIllisibleRemplaceParSesPassagesIndexes() {
        String ref = document("Specs/Avoirs.docx", "Avoirs", List.of("Contexte", "Exigences"), 1_000, false);

        ProjectKnowledge k = withDocuments().forSpecification(session, List.of());

        assertThat(documents(k)).singleElement().satisfies(e -> {
            assertThat(e.ref()).isEqualTo(ref);
            assertThat(e.title()).isEqualTo("Avoirs");
            assertThat(e.text()).startsWith("Contexte").contains("\n\nExigences");
        });
        assertThat(w.folder.reads).containsExactly("Specs/Avoirs.docx");

        // source supprimée entre-temps : rien n'est relu, les passages servent
        w.folder.reads.clear();
        w.docSources.delete(documents.id());
        SelectKnowledge orphan = new SelectKnowledge(w.projects, index, embedding, analyze, StoryFamily.none(),
                w.docSources, w.folder);
        assertThat(documents(orphan.forSpecification(session, List.of()))).singleElement()
                .satisfies(e -> assertThat(e.text()).startsWith("Contexte"));
        assertThat(w.folder.reads).isEmpty();
    }

    @Test
    void adrDUnDossierDeDocumentsSuitLaVoieDesAdr() {
        String ref = DocumentFolder.ref(documents, "Decisions/0017-avoirs.docx");
        index.save(KnowledgeItem.adr(P, ref, 0, "ADR-017 Avoir total sur facture",
                "Décision : l'avoir total annule la facture émise. avoir facture",
                AdrStatus.Statement.of(AdrStatus.ACCEPTED, null), T, T), null, null);

        ProjectKnowledge k = withDocuments().forSpecification(session, List.of());

        assertThat(k.entries()).filteredOn(e -> e.ref().equals(ref)).singleElement()
                .satisfies(e -> assertThat(e.kind()).isEqualTo(KnowledgeKind.ADR));
        assertThat(w.folder.reads).isEmpty();
    }

    @Test
    void deuxPreparationsIdentiques() {
        document("Specs/Avoirs.docx", "Avoirs", List.of("Contexte", "Exigences"), 1_000, true);
        SelectKnowledge documented = withDocuments();
        assertThat(documented.forSpecification(session, List.of()))
                .isEqualTo(documented.forSpecification(session, List.of()));
    }

    @Test
    void documentDesigneParLInstructionSeule() {
        for (int i = 1; i <= 3; i++) {
            document("Avoirs " + i + ".docx", "Avoirs " + i, List.of("Contexte"), 1_000, true);
        }
        String parking = DocumentFolder.ref(documents, "Parking.pdf");
        index.save(KnowledgeItem.docSection(P, parking + "#0", 0, "Parking — Stationnement",
                "Stationnement des vélos au siège.", null, T, T), null, null);
        w.folder.put(documents.location(), "Parking.pdf", 1_000, T,
                FakeDocumentFolder.page(new Page(parking, "Parking", "Stationnement des vélos au siège.", List.of())));
        SelectKnowledge documented = withDocuments();

        assertThat(documents(documented.forSpecification(session, List.of())))
                .extracting(ProjectKnowledge.Entry::ref).doesNotContain(parking);
        assertThat(documents(documented.forEdit(session, List.of(), "Ajoute le stationnement des vélos.", "")))
                .extracting(ProjectKnowledge.Entry::ref).contains(parking);
    }
    // 007 : documents désignés avec @, transmis d'office avant la recherche (research.md R6)

    Session sessionWith(String need) {
        return Session.start(UUID.randomUUID(), P, Need.of(need), null, w.clock.now());
    }

    static List<ProjectKnowledge.Entry> designated(ProjectKnowledge k) {
        return k.entries().stream().filter(ProjectKnowledge.Entry::designated).toList();
    }

    @Test
    void documentMentionneDansUneReponseTransmisEnPremier() {
        String ref = document("Specs/Parking.docx", "Parking", List.of("Stationnement"), 1_000, true);

        ProjectKnowledge k = withDocuments().forSpecification(session,
                List.of(answer("q1", "les informations sont dans le document @Parking")));

        assertThat(designated(k)).singleElement().satisfies(e -> {
            assertThat(e.ref()).isEqualTo(ref);
            assertThat(e.title()).isEqualTo("Parking");
            assertThat(e.text()).startsWith("# Stationnement");
        });
        assertThat(k.entries()).filteredOn(e -> e.ref().equals(ref)).hasSize(1);
        int position = k.entries().indexOf(designated(k).getFirst());
        assertThat(k.entries().subList(0, position)).allMatch(e -> e.role() != null);
        assertThat(total(k)).isLessThanOrEqualTo(ProjectKnowledge.MAX_TOTAL);
    }

    @Test
    void pageAntoraMentionneeRecomposeeDansSonOrdre() {
        index.save(KnowledgeItem.docSection(P, "facturation/regles/avoir.html#_plafond", 0, "Plafond",
                "Le plafond est le restant dû.", null, T, T), null, null);
        index.save(KnowledgeItem.docSection(P, "facturation/regles/avoir.html#_delai", 0, "Délai",
                "Le délai est de trente jours.", null, T, T), null, null);

        ProjectKnowledge k = select.forSpecification(sessionWith(NEED + " Voir @Règles de l'avoir."), List.of());

        assertThat(designated(k)).singleElement().satisfies(e -> {
            assertThat(e.ref()).isEqualTo("facturation/regles/avoir.html");
            assertThat(e.title()).isEqualTo("Règles de l'avoir");
            assertThat(e.text()).startsWith("Un avoir ne dépasse pas")
                    .contains("\n\nLe plafond est le restant dû.\n\nLe délai est de trente jours.");
        });
    }

    @Test
    void adrMentionneSuitLaVoieDesAdr() {
        ProjectKnowledge k = select.forSpecification(sessionWith(NEED + " Respecter @ADR-2 et @ADR-3."), List.of());

        // ADR-002 est remplacé par l'ADR-005 ; l'ADR-003 est obsolète
        assertThat(designated(k)).singleElement().satisfies(e -> {
            assertThat(e.kind()).isEqualTo(KnowledgeKind.ADR);
            assertThat(e.ref()).isEqualTo("facturation/adr/adr-005.html");
        });
        assertThat(k.entries()).filteredOn(e -> e.ref().equals("facturation/adr/adr-005.html")).hasSize(1);
        assertThat(k.entries()).extracting(ProjectKnowledge.Entry::ref).doesNotContain("facturation/adr/adr-003.html");
    }

    @Test
    void quatreDocumentsDesignesAuPlusCoupesAUneSection() {
        StringBuilder need = new StringBuilder(NEED);
        for (int i = 1; i <= 5; i++) {
            document("D" + i + ".docx", "D" + i, List.of("Partie"), 2_500, true);
            need.append(" @D").append(i);
        }
        document("Long.docx", "Long", List.of("Un", "Deux", "Trois", "Quatre", "Cinq"), 3_000, true);

        SelectKnowledge documented = withDocuments();
        List<ProjectKnowledge.Entry> four = designated(documented.forSpecification(sessionWith(need.toString()), List.of()));
        assertThat(four).extracting(ProjectKnowledge.Entry::title).containsExactly("D1", "D2", "D3", "D4");
        assertThat(four.stream().mapToInt(e -> e.title().length() + e.text().length()).sum())
                .isLessThanOrEqualTo(SelectKnowledge.MAX_MENTIONED_TEXT);

        ProjectKnowledge.Entry cut = designated(documented.forSpecification(sessionWith(NEED + " @Long"), List.of()))
                .getFirst();
        assertThat(cut.title().length() + cut.text().length()).isLessThanOrEqualTo(SelectKnowledge.MAX_MENTIONED_PART);
        assertThat(cut.text()).contains("# Deux").doesNotContain("# Quatre").endsWith("[document tronqué]");
    }

    @Test
    void laFicheGeneraleLEmporteSurLesDocumentsDesignes() {
        sheet(text("## Fiche générale", 20_000));
        document("Long.docx", "Long", List.of("Un", "Deux", "Trois"), 3_000, true);

        ProjectKnowledge k = withDocuments().forSpecification(sessionWith(NEED + " @Long"), List.of());

        ProjectKnowledge.Entry cut = designated(k).getFirst();
        assertThat(cut.text()).endsWith("[document tronqué]");
        assertThat(total(k)).isLessThanOrEqualTo(ProjectKnowledge.MAX_TOTAL - SelectKnowledge.MAX_ARCHITECTURE
                - SelectKnowledge.MAX_TICKET_TEXT - SelectKnowledge.MAX_SUBTASK_TEXT + size(k, KnowledgeKind.TICKET));
    }

    @Test
    void mentionAmbigueOuIntrouvableNonTransmise() {
        document("Delivery/Suivi.xlsx", "Suivi", List.of("Jalons"), 500, true);
        document("Archives/Suivi.xlsx", "Suivi", List.of("Jalons"), 500, true);

        ProjectKnowledge k = withDocuments().forSpecification(sessionWith(NEED + " @Suivi et @Inconnu"), List.of());

        assertThat(designated(k)).isEmpty();
        ProjectKnowledge precise = withDocuments().forSpecification(sessionWith(NEED + " @Archives/Suivi.xlsx"),
                List.of());
        assertThat(designated(precise)).extracting(ProjectKnowledge.Entry::ref)
                .containsExactly(DocumentFolder.ref(documents, "Archives/Suivi.xlsx"));
    }

    @Test
    void mentionPourLesQuestionsEtLaModification() {
        document("Specs/Parking.docx", "Parking", List.of("Stationnement"), 1_000, true);
        SelectKnowledge documented = withDocuments();

        assertThat(designated(documented.forQuestions(sessionWith(NEED + " @Parking"), List.of()))).hasSize(1);
        assertThat(designated(documented.forEdit(session, List.of(), "Ajoute ce que dit @Parking.", ""))).hasSize(1);
        assertThat(designated(documented.forEdit(session, List.of(), "Ajoute un cas d'erreur.", ""))).isEmpty();
    }

    @Test
    void deuxPreparationsIdentiquesAvecMentions() {
        document("Specs/Parking.docx", "Parking", List.of("Stationnement"), 1_000, true);
        SelectKnowledge documented = withDocuments();
        Session mentioning = sessionWith(NEED + " @Parking @ADR-1");
        assertThat(documented.forSpecification(mentioning, List.of()))
                .isEqualTo(documented.forSpecification(mentioning, List.of()));
    }
}
