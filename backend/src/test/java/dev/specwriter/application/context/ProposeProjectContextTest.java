package dev.specwriter.application.context;

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
                proposals, w.clock);
        accept = new AcceptContextProposal(w.projects, proposals, w.clock);
    }

    static SourceFacts facts(String ref) {
        return new SourceFacts(List.of(new SourceFacts.Fact(Category.ACTOR, "Le comptable lettre les paiements.", List.of(ref))));
    }

    static ProjectContextDraft context(String ref) {
        return new ProjectContextDraft("## Vocabulaire\n\n## Acteurs\n- Le comptable lettre les paiements. [source : " + ref
                + "]\n\n## Règles métier\n\n## Contraintes\n\n## Conventions\n");
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

        assertThat(p.markdown()).contains("[source : " + PAGE + "]");
        assertThat(p.sources()).containsExactly(PAGE);
        assertThat(p.unread()).containsExactly(new Unread(SITE + "tarifs.html", UnreadReason.HTTP_ERROR),
                new Unread(SITE + "recherche.html", UnreadReason.NOT_ANTORA));
        assertThat(p.unifiedDiff()).isNotBlank();
        // les tickets passent par la conversion wiki vers Markdown, avec leur clé pour référence
        SummarizeSourcesRequest batch = w.llm.summarizeRequests.getFirst();
        assertThat(batch.sources()).extracting(SummarizeSourcesRequest.Source::ref).containsExactly(PAGE, "FACT-1");
        assertThat(batch.sources().get(1).text()).isEqualTo("h2. Besoin\nLe *gestionnaire* valide.");
        // rien n'est enregistré avant acceptation
        assertThat(w.projects.find(project.id()).orElseThrow().context()).isEqualTo(ProjectContext.NONE);
        // le résultat de lecture est noté sur la source, sans contenu
        DocSource source = w.docSources.list(project.id()).getFirst();
        // la page hors Antora est écartée, pas en erreur
        assertThat(source.lastResult()).isEqualTo(new DocSource.ReadSummary(1, 1, false));
        assertThat(source.lastReadAt()).isEqualTo(w.clock.now());

        Project accepted = accept.run(project.id(), p.id());
        assertThat(accepted.context().origin()).isEqualTo(ProjectContext.Origin.GENERATED);
        assertThat(w.projects.find(project.id()).orElseThrow().context().markdown()).isEqualTo(p.markdown().strip());
        // une proposition ne s'accepte qu'une fois
        assertThatThrownBy(() -> accept.run(project.id(), p.id())).isInstanceOfSatisfying(DomainException.class,
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

    @Test
    void referenceInconnueCorrigeeTroisEssaisAuPlus() {
        w.llm.facts.add(facts(PAGE));
        w.llm.contexts.add(context("https://inventee.example/page.html"));
        w.llm.contexts.add(context(PAGE));
        propose.run(project.id());
        assertThat(w.llm.contextRequests).hasSize(2);
        assertThat(w.llm.contextRequests.get(0).correction()).isEmpty();
        assertThat(w.llm.contextRequests.get(1).correction()).contains("https://inventee.example/page.html");

        w.llm.facts.add(facts(PAGE));
        for (int i = 0; i < 3; i++) {
            w.llm.contexts.add(context("FACT-999"));
        }
        assertThatThrownBy(() -> propose.run(project.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.LLM_INVALID_OUTPUT));
    }

    @Test
    void faitCitantUneSourceHorsDuLotCorrige() {
        w.llm.facts.add(facts("https://ailleurs.example/"));
        w.llm.facts.add(facts(PAGE));
        w.llm.contexts.add(context(PAGE));
        propose.run(project.id());
        assertThat(w.llm.summarizeRequests).hasSize(2);
        assertThat(w.llm.summarizeRequests.get(1).correction()).contains("https://ailleurs.example/");
    }

    @Test
    void ficheDePlusDe20000CaracteresCorrigee() {
        w.llm.facts.add(facts(PAGE));
        w.llm.contexts.add(new ProjectContextDraft("- trop long [source : " + PAGE + "]\n" + "x".repeat(20_001)));
        w.llm.contexts.add(context(PAGE));
        propose.run(project.id());
        assertThat(w.llm.contextRequests.get(1).correction()).contains("20 000");
    }

    @Test
    void consentementCopilotExigeAvantToutEnvoi() {
        JiraWorld bare = new JiraWorld().configureJira();
        bare.projects.save(project);
        ProposeProjectContext p = new ProposeProjectContext(bare.projects, bare.docSources, bare.docs, bare.jira,
                bare.tracker, bare.markup, bare.llm, bare.policy, proposals, bare.clock);
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
                noJira.tracker, noJira.markup, noJira.llm, noJira.policy, proposals, noJira.clock);
        assertThatThrownBy(() -> p.run(project.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(noJira.llm.calls()).isZero();
    }

    @Test
    void acceptationRefuseeSiLeContexteAChange() {
        w.llm.facts.add(facts(PAGE));
        w.llm.contexts.add(context(PAGE));
        ContextProposalStore.ContextProposal p = propose.run(project.id());
        new EditContext(w.projects, w.clock).run(project.id(), "## Acteurs\n- modifié à la main");
        assertThatThrownBy(() -> accept.run(project.id(), p.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONTEXT_CHANGED));
        assertThat(w.projects.find(project.id()).orElseThrow().context().origin()).isEqualTo(ProjectContext.Origin.EDITED);
    }

    @Test
    void laFicheActuelleEstTransmiseALaRegeneration() {
        new EditContext(w.projects, w.clock).run(project.id(), "## Acteurs\n- Le directeur signe.");
        w.llm.facts.add(facts(PAGE));
        w.llm.contexts.add(context(PAGE));
        ContextProposalStore.ContextProposal p = propose.run(project.id());
        assertThat(w.llm.contextRequests.getFirst().currentContext()).isEqualTo("## Acteurs\n- Le directeur signe.");
        assertThat(p.unifiedDiff()).startsWith("--- ");
        // « Garder la mienne » : la proposition est oubliée, la fiche reste la même
        proposals.discard(project.id(), p.id());
        assertThatThrownBy(() -> accept.run(project.id(), p.id())).isInstanceOf(DomainException.class);
        assertThat(w.projects.find(project.id()).orElseThrow().context().markdown()).isEqualTo("## Acteurs\n- Le directeur signe.");
    }
}
