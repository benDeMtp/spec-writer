package dev.specwriter.application.context;

import java.util.Set;
import dev.specwriter.application.context.GenerationProgress.Step;
import dev.specwriter.domain.project.ContextSheets;
import dev.specwriter.domain.project.ContextSheet;
import dev.specwriter.domain.project.ContextQuestion;
import dev.specwriter.domain.llm.BuildContextRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.llm.ProjectContextDraft;
import dev.specwriter.domain.llm.SourceFacts;
import dev.specwriter.domain.llm.SourceFacts.Category;
import dev.specwriter.domain.llm.SummarizeSourcesRequest;
import dev.specwriter.domain.port.DocumentationReader.Page;
import dev.specwriter.domain.port.DocumentationReader.Result;
import dev.specwriter.domain.port.DocumentationReader.Unread;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.JiraProjectKey;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import dev.specwriter.domain.project.ProjectName;
import dev.specwriter.support.JiraWorld;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Fiche de contexte proposée par l'IA (FR-013a à FR-013e, research.md R4 de 002, T021). */
class ProposeProjectContextTest {

    static final String SITE = "https://docs.example.com/billing/";
    static final String PAGE = SITE + "acteurs.html";

    final JiraWorld w = new JiraWorld().grant(ConsentService.COPILOT).grant(ConsentService.JIRA).configureJira();
    final ContextProposalStore proposals = new ContextProposalStore(w.diff, w.ids, w.clock);
    Project project;
    ProposeProjectContext propose;
    AcceptContextProposal accept;

    @BeforeEach
    void setUp() {
        project = Project.create(UUID.randomUUID(), ProjectName.of("Facturation"), null, JiraProjectKey.ofNullable("FACT"),
                Instant.EPOCH);
        w.projects.save(project);
        w.docSources.save(DocSource.create(UUID.randomUUID(), project.id(), DocSource.Kind.URL, SITE));
        w.docs.sites.put(SITE, new Result(List.of(new Page(PAGE, "Acteurs", "Le comptable lettre les paiements.")),
                List.of(new Unread(SITE + "tarifs.html", UnreadReason.HTTP_ERROR),
                        new Unread(SITE + "recherche.html", UnreadReason.NOT_ANTORA))));
        propose = new ProposeProjectContext(w.projects, w.docSources, w.docs, w.jira, w.tracker, w.markup, w.llm, w.policy,
                proposals, w.contextQuestions, w.clock);
        accept = new AcceptContextProposal(w.projects, proposals, w.clock);
    }

    static SourceFacts facts(String ref) {
        return new SourceFacts(List.of(new SourceFacts.Fact(Category.ACTOR, "Le comptable lettre les paiements.", List.of(ref))));
    }

    static ProjectContextDraft context(String ref) {
        return new ProjectContextDraft("## Vision et objectifs\n\n## Acteurs\n- Le comptable lettre les paiements. [source : "
                + ref + "]\n\n## Vocabulaire\n\n## Règles métier\n\n## Contraintes fonctionnelles\n",
                "## Architecture\n\n## Contraintes techniques\n\n## Conventions de code\n\n## Intégrations\n", List.of());
    }

    static ProjectContextDraft draft(String functional, String technical, ProjectContextDraft.Question... questions) {
        return new ProjectContextDraft(functional, technical, List.of(questions));
    }

    static ProjectContextDraft.Question question(ContextSheet sheet, String label) {
        return new ProjectContextDraft.Question(sheet, label, "Les sources se contredisent", List.of("A", "B"));
    }

    void ticket(int n, String description) {
        w.tracker.issues.put("FACT-" + n, new JiraIssue("FACT-" + n, "Facture " + n, description,
                Instant.parse("2026-10-01T00:00:00Z").plusSeconds(n * 60L)));
    }

    @Test
    void propositionCiteeGardeeEnMemoireRienNEstEnregistre() {
        ticket(1, "h2. Besoin\nLe *gestionnaire* valide.");
        w.llm.facts.add(facts(PAGE));
        w.llm.contexts.add(context(PAGE));

        ContextProposalStore.ContextProposal p = propose.run(project.id());

        assertThat(p.functional().markdown()).contains("[source : " + PAGE + "]");
        assertThat(p.sources()).containsExactly(PAGE);
        assertThat(p.unread()).containsExactly(new Unread(SITE + "tarifs.html", UnreadReason.HTTP_ERROR),
                new Unread(SITE + "recherche.html", UnreadReason.NOT_ANTORA));
        assertThat(p.functional().unifiedDiff()).isNotBlank();
        assertThat(p.technical().changed()).isTrue();
        // les tickets passent par la conversion wiki vers Markdown, avec leur clé pour référence
        SummarizeSourcesRequest batch = w.llm.summarizeRequests.getFirst();
        assertThat(batch.sources()).extracting(SummarizeSourcesRequest.Source::ref).containsExactly(PAGE, "FACT-1");
        assertThat(batch.sources().get(1).text()).isEqualTo("h2. Besoin\nLe *gestionnaire* valide.");
        // rien n'est enregistré avant acceptation
        assertThat(w.projects.find(project.id()).orElseThrow().contexts()).isEqualTo(ContextSheets.NONE);
        // le résultat de lecture est noté sur la source, sans contenu
        DocSource source = w.docSources.list(project.id()).getFirst();
        // la page hors Antora est écartée, pas en erreur
        assertThat(source.lastResult()).isEqualTo(new DocSource.ReadSummary(1, 1, false));
        assertThat(source.lastReadAt()).isEqualTo(w.clock.now());

        Project accepted = accept.run(project.id(), p.id(), Set.of(ContextSheet.FUNCTIONAL));
        assertThat(accepted.context(ContextSheet.FUNCTIONAL).origin()).isEqualTo(ProjectContext.Origin.GENERATED);
        assertThat(accepted.context(ContextSheet.TECHNICAL)).isEqualTo(ProjectContext.NONE);
        assertThat(w.projects.find(project.id()).orElseThrow().context(ContextSheet.FUNCTIONAL).markdown())
                .isEqualTo(p.functional().markdown());
        // une fiche ne s'accepte qu'une fois
        assertThatThrownBy(() -> accept.run(project.id(), p.id(), Set.of(ContextSheet.FUNCTIONAL)))
                .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void lotsDEnviron40000CaracteresPuisUneFiche() {
        w.docs.sites.put(SITE, new Result(IntStream.range(0, 5)
                .mapToObj(i -> new Page(SITE + "p" + i + ".html", "Page " + i, "x".repeat(15_000))).toList(), List.of()));
        for (int i = 0; i < 3; i++) {
            w.llm.facts.add(facts(SITE + "p" + (2 * i) + ".html"));
        }
        w.llm.contexts.add(context(SITE + "p0.html"));
        propose.run(project.id());
        assertThat(w.llm.summarizeRequests).hasSize(3);
        assertThat(w.llm.summarizeRequests).allSatisfy(r -> assertThat(r.sources().stream()
                .mapToInt(s -> s.text().length()).sum()).isLessThanOrEqualTo(ProposeProjectContext.BATCH_CHARS));
        assertThat(w.llm.contextRequests).singleElement().satisfies(r -> assertThat(r.facts()).hasSize(3));
    }

    @Test
    void les200TicketsLesPlusRecentsParPagesDe50() {
        IntStream.rangeClosed(1, 230).forEach(n -> ticket(n, "Description " + n));
        w.docs.sites.put(SITE, new Result(List.of(), List.of()));
        w.llm.facts.add(facts("FACT-230"));
        w.llm.facts.add(facts("FACT-100"));
        w.llm.contexts.add(context("FACT-230"));
        propose.run(project.id());
        assertThat(w.tracker.calls).containsSubsequence("recent FACT 0 50", "recent FACT 50 50", "recent FACT 100 50",
                "recent FACT 150 50").doesNotContain("recent FACT 200 50");
        List<String> refs = w.llm.summarizeRequests.stream().flatMap(r -> r.sources().stream())
                .map(SummarizeSourcesRequest.Source::ref).toList();
        assertThat(refs).hasSize(200).contains("FACT-230", "FACT-31").doesNotContain("FACT-30");
    }

    /** Aucune affirmation vérifiable : seul cas, avec une fiche trop longue, où le LLM est rappelé. */
    @Test
    void touteAffirmationInvérifiableCorrigeeTroisEssaisAuPlus() {
        w.llm.facts.add(facts(PAGE));
        w.llm.contexts.add(context("https://inventee.example/page.html"));
        w.llm.contexts.add(context(PAGE));
        propose.run(project.id());
        assertThat(w.llm.contextRequests).hasSize(2);
        assertThat(w.llm.contextRequests.get(0).correction()).isEmpty();
        assertThat(w.llm.contextRequests.get(1).correction()).contains("aucune affirmation ne cite une source lue")
                .contains("https://inventee.example/page.html");

        w.llm.facts.add(facts(PAGE));
        for (int i = 0; i < 3; i++) {
            w.llm.contexts.add(context("FACT-999"));
        }
        assertThatThrownBy(() -> propose.run(project.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.LLM_INVALID_OUTPUT));
    }

    /** Fait hors du lot écarté, référence ramenée : aucun nouvel appel (FR-013g). */
    @Test
    void faitsReparesSansNouvelAppel() {
        List<GenerationError> errors = new ArrayList<>();
        w.llm.facts.add(new SourceFacts(List.of(
                new SourceFacts.Fact(Category.ACTOR, "Le comptable lettre les paiements.", List.of("billing/acteurs.html")),
                new SourceFacts.Fact(Category.ACTOR, "Inventé.", List.of("https://ailleurs.example/")),
                new SourceFacts.Fact(Category.ACTOR, "Sans source.", List.of()))));
        w.llm.contexts.add(context(PAGE));
        propose.run(project.id(), recording(errors));
        assertThat(w.llm.summarizeRequests).hasSize(1);
        assertThat(w.llm.contextRequests.getFirst().facts()).singleElement()
                .satisfies(f -> assertThat(f.refs()).containsExactly(PAGE));
        assertThat(errors).filteredOn(e -> e.kind() == GenerationError.Kind.REPAIRED).singleElement().satisfies(e -> {
            assertThat(e.reason()).isEqualTo("extraction des faits");
            assertThat(e.message()).contains("1 référence ramenée à une source du lot", "2 faits écartés",
                    "https://ailleurs.example/").doesNotContain("Inventé", "Sans source");
        });
    }

    /** Affirmation à la source inconnue retirée, citation ramenée : la fiche est proposée au premier essai. */
    @Test
    void ficheRepareeSansNouvelAppel() {
        List<GenerationError> errors = new ArrayList<>();
        w.llm.facts.add(facts(PAGE));
        w.llm.contexts.add(draft("- ok [source : acteurs.html]\n- inventé [source : FACT-404]", "- tech [source : " + PAGE + "]"));
        ContextProposalStore.ContextProposal p = propose.run(project.id(), recording(errors));
        assertThat(w.llm.contextRequests).hasSize(1);
        assertThat(p.functional().markdown()).isEqualTo("- ok [source : " + PAGE + "]");
        assertThat(p.sources()).containsExactly(PAGE);
        assertThat(errors).filteredOn(e -> e.kind() == GenerationError.Kind.REPAIRED).singleElement()
                .satisfies(e -> assertThat(e.message()).contains("fiche fonctionnelle", "1 référence ramenée à une source lue",
                        "1 affirmation retirée", "FACT-404"));
    }

    static GenerationProgress recording(List<GenerationError> errors) {
        return new GenerationProgress() {
            @Override
            public void step(Step step, int total) {
            }

            @Override
            public void advance(String current, int done, int total) {
            }

            @Override
            public void error(GenerationError error) {
                errors.add(error);
            }
        };
    }

    @Test
    void ficheDePlusDe20000CaracteresCorrigee() {
        w.llm.facts.add(facts(PAGE));
        w.llm.contexts.add(draft("- court [source : " + PAGE + "]", "- trop long [source : " + PAGE + "]\n" + "x".repeat(20_001)));
        w.llm.contexts.add(context(PAGE));
        propose.run(project.id());
        assertThat(w.llm.contextRequests.get(1).correction()).contains("fiche technique dépasse 20 000");
    }

    @Test
    void consentementCopilotExigeAvantToutEnvoi() {
        JiraWorld bare = new JiraWorld().configureJira();
        bare.projects.save(project);
        ProposeProjectContext p = new ProposeProjectContext(bare.projects, bare.docSources, bare.docs, bare.jira,
                bare.tracker, bare.markup, bare.llm, bare.policy, proposals, bare.contextQuestions, bare.clock);
        assertThatThrownBy(() -> p.run(project.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONSENT_REQUIRED));
        assertThat(bare.llm.calls()).isZero();
        assertThat(bare.docs.reads).isEmpty();
    }

    @Test
    void sansSourceNiTicketRienAEnvoyer() {
        JiraWorld noJira = new JiraWorld().grant(ConsentService.COPILOT);
        noJira.projects.save(project);
        ProposeProjectContext p = new ProposeProjectContext(noJira.projects, noJira.docSources, noJira.docs, noJira.jira,
                noJira.tracker, noJira.markup, noJira.llm, noJira.policy, proposals, noJira.contextQuestions, noJira.clock);
        assertThatThrownBy(() -> p.run(project.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(noJira.llm.calls()).isZero();
    }

    @Test
    void acceptationRefuseeSiLeContexteAChange() {
        w.llm.facts.add(facts(PAGE));
        w.llm.contexts.add(context(PAGE));
        ContextProposalStore.ContextProposal p = propose.run(project.id());
        new EditContext(w.projects, w.clock).run(project.id(), ContextSheet.TECHNICAL, "## Architecture\n- modifiée à la main");
        assertThatThrownBy(() -> accept.run(project.id(), p.id(), Set.of(ContextSheet.TECHNICAL)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CONTEXT_CHANGED));
        assertThat(w.projects.find(project.id()).orElseThrow().context(ContextSheet.TECHNICAL).origin())
                .isEqualTo(ProjectContext.Origin.EDITED);
        // l'autre fiche, inchangée, reste acceptable
        assertThat(accept.run(project.id(), p.id(), Set.of(ContextSheet.FUNCTIONAL)).context(ContextSheet.FUNCTIONAL)
                .origin()).isEqualTo(ProjectContext.Origin.GENERATED);
    }

    @Test
    void laFicheActuelleEstTransmiseALaRegeneration() {
        new EditContext(w.projects, w.clock).run(project.id(), ContextSheet.FUNCTIONAL, "## Acteurs\n- Le directeur signe.");
        new EditContext(w.projects, w.clock).run(project.id(), ContextSheet.TECHNICAL, "## Architecture\n- Monolithe.");
        w.llm.facts.add(facts(PAGE));
        w.llm.contexts.add(context(PAGE));
        ContextProposalStore.ContextProposal p = propose.run(project.id());
        assertThat(w.llm.contextRequests.getFirst().currentFunctional()).isEqualTo("## Acteurs\n- Le directeur signe.");
        assertThat(w.llm.contextRequests.getFirst().currentTechnical()).isEqualTo("## Architecture\n- Monolithe.");
        assertThat(p.functional().unifiedDiff()).startsWith("--- ");
        // « Garder la mienne » : la proposition est oubliée, la fiche reste la même
        proposals.discard(project.id(), p.id());
        assertThatThrownBy(() -> accept.run(project.id(), p.id(), Set.of(ContextSheet.FUNCTIONAL)))
                .isInstanceOf(DomainException.class);
        assertThat(w.projects.find(project.id()).orElseThrow().context(ContextSheet.FUNCTIONAL).markdown())
                .isEqualTo("## Acteurs\n- Le directeur signe.");
    }

    @Test
    void deuxFichesSourceesChacuneAccepteeSeparement() {
        ticket(1, "Les services communiquent par une file JMS.");
        w.llm.facts.add(new SourceFacts(List.of(
                new SourceFacts.Fact(Category.ACTOR, "Le comptable lettre les paiements.", List.of(PAGE)),
                new SourceFacts.Fact(Category.INTEGRATION, "Les services communiquent par une file JMS.", List.of("FACT-1")))));
        w.llm.contexts.add(draft("## Acteurs\n- Le comptable lettre les paiements. [source : " + PAGE + "]",
                "## Intégrations\n- Une file JMS relie les services. [source : FACT-1]"));
        ContextProposalStore.ContextProposal p = propose.run(project.id());
        assertThat(p.sources()).containsExactly(PAGE, "FACT-1");
        assertThat(p.technical().unifiedDiff()).contains("fiche technique actuelle");

        accept.run(project.id(), p.id(), Set.of(ContextSheet.TECHNICAL));
        Project after = w.projects.find(project.id()).orElseThrow();
        assertThat(after.context(ContextSheet.TECHNICAL).markdown()).contains("file JMS");
        assertThat(after.context(ContextSheet.FUNCTIONAL)).isEqualTo(ProjectContext.NONE);
        assertThat(proposals.find(project.id(), p.id())).isPresent();
        accept.run(project.id(), p.id(), Set.of(ContextSheet.FUNCTIONAL));
        // les deux fiches acceptées : la proposition est oubliée
        assertThat(proposals.find(project.id(), p.id())).isEmpty();
        assertThatThrownBy(() -> accept.run(project.id(), p.id(), Set.of())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void referenceInconnueDansLaFicheTechniqueRetiree() {
        w.llm.facts.add(facts(PAGE));
        w.llm.contexts.add(draft("- ok [source : " + PAGE + "]", "## Architecture\n- inventé [source : FACT-404]"));
        ContextProposalStore.ContextProposal p = propose.run(project.id());
        assertThat(w.llm.contextRequests).hasSize(1);
        assertThat(p.technical().markdown()).isEqualTo("## Architecture");
    }

    @Test
    void dixQuestionsAuPlusReponsesEnregistreesEtCitables() {
        w.llm.facts.add(facts(PAGE));
        ProjectContextDraft.Question[] many = IntStream.range(0, 12)
                .mapToObj(i -> question(i % 2 == 0 ? ContextSheet.FUNCTIONAL : ContextSheet.TECHNICAL, "Question " + i + " ?"))
                .toArray(ProjectContextDraft.Question[]::new);
        w.llm.contexts.add(draft("- ok [source : " + PAGE + "]", "", many));
        ContextProposalStore.ContextProposal p = propose.run(project.id());
        assertThat(p.questions()).hasSize(10).allSatisfy(q -> assertThat(q.state()).isEqualTo(ContextProposalStore.QuestionState.OPEN));

        AnswerContextQuestion answer = new AnswerContextQuestion(proposals, w.contextQuestions, w.ids, w.clock);
        ContextQuestion q1 = answer.run(project.id(), p.id(), p.questions().get(1).id(), "PostgreSQL 16", false);
        ContextQuestion q2 = answer.run(project.id(), p.id(), p.questions().get(0).id(), null, true);
        assertThat(q1.ref()).isEqualTo("Q-1");
        assertThat(q1.sheet()).isEqualTo(ContextSheet.TECHNICAL);
        assertThat(q2.number()).isEqualTo(2);
        assertThat(q2.state()).isEqualTo(ContextQuestion.State.SKIPPED);
        // répondre de nouveau à la même question garde son numéro
        assertThat(answer.run(project.id(), p.id(), p.questions().get(1).id(), "PostgreSQL 17", false).number()).isEqualTo(1);
        assertThat(proposals.find(project.id(), p.id()).orElseThrow().questions().get(1).number()).isEqualTo(1);

        // régénération : la réponse est transmise et citable, la question passée n'est pas reposée
        w.llm.facts.add(facts(PAGE));
        w.llm.contexts.add(draft("- ok [source : " + PAGE + "]", "## Contraintes techniques\n- PostgreSQL 17. [source : Q-1]",
                question(ContextSheet.FUNCTIONAL, "  question 0 ?  ")));
        List<GenerationError> errors = new ArrayList<>();
        int calls = w.llm.contextRequests.size();
        ContextProposalStore.ContextProposal again = propose.run(project.id(), recording(errors));
        // la question déjà posée est écartée sans nouvel appel ; le motif ne cite que son numéro
        assertThat(w.llm.contextRequests).hasSize(calls + 1);
        assertThat(errors).filteredOn(e -> e.kind() == GenerationError.Kind.REPAIRED).singleElement()
                .satisfies(e -> assertThat(e.message()).contains("1 question déjà posée écartée (Q-2)")
                        .doesNotContain("question 0"));
        BuildContextRequest first = w.llm.contextRequests.getLast();
        assertThat(first.answers()).containsExactly(
                new BuildContextRequest.Answer("Q-1", ContextSheet.TECHNICAL, "Question 1 ?", "PostgreSQL 17"));
        assertThat(first.skipped()).containsExactly(new BuildContextRequest.Skipped(ContextSheet.FUNCTIONAL, "Question 0 ?"));
        assertThat(again.sources()).contains("Q-1");
        assertThat(again.questions()).isEmpty();

        ManageContextQuestions history = new ManageContextQuestions(w.projects, w.contextQuestions, w.clock);
        assertThat(history.list(project.id())).extracting(ContextQuestion::ref).containsExactly("Q-1", "Q-2");
        assertThat(history.update(project.id(), 2, "Simplifier le lettrage", false).state())
                .isEqualTo(ContextQuestion.State.ANSWERED);
        assertThatThrownBy(() -> history.update(project.id(), 9, "x", false)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
