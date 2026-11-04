package dev.specwriter.application.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.application.context.ContextGenerations.Snapshot;
import dev.specwriter.application.context.ContextGenerations.Status;
import dev.specwriter.application.context.GenerationError.Kind;
import dev.specwriter.application.context.GenerationProgress.Step;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.llm.ProjectContextDraft;
import dev.specwriter.domain.llm.SourceFacts;
import dev.specwriter.domain.llm.SourceFacts.Category;
import dev.specwriter.domain.port.DocumentationReader.Page;
import dev.specwriter.domain.port.DocumentationReader.Result;
import dev.specwriter.domain.port.DocumentationReader.Unread;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.domain.project.ContextQuestion;
import dev.specwriter.domain.project.ContextSheet;
import dev.specwriter.domain.project.ContextSheets;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.JiraProjectKey;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import dev.specwriter.domain.project.ProjectName;
import dev.specwriter.support.JiraWorld;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Génération du contexte suivie, interrompue et détaillée (FR-013f, FR-013g, research.md R14, T065). */
class ContextGenerationsTest {

    static final String SITE = "https://docs.example.com/billing/";
    static final String PAGE = SITE + "acteurs.html";
    static final String SECRET_TEXT = "Le comptable lettre les paiements.";

    final JiraWorld w = new JiraWorld().grant(ConsentService.COPILOT).grant(ConsentService.JIRA).configureJira();
    final ContextProposalStore proposals = new ContextProposalStore(w.diff, w.ids, w.clock);
    final List<Runnable> queued = new ArrayList<>();
    Project project;
    ContextGenerations generations;

    @BeforeEach
    void setUp() {
        project = Project.create(UUID.randomUUID(), ProjectName.of("Facturation"), null, JiraProjectKey.ofNullable("FACT"),
                Instant.EPOCH);
        w.projects.save(project);
        w.docSources.save(DocSource.create(UUID.randomUUID(), project.id(), DocSource.Kind.URL, SITE));
        w.docs.sites.put(SITE, new Result(List.of(new Page(PAGE, "Acteurs", SECRET_TEXT)),
                List.of(new Unread(SITE + "tarifs.html", UnreadReason.HTTP_ERROR, 500),
                        new Unread(SITE + "recherche.html", UnreadReason.NOT_ANTORA))));
        IntStream.rangeClosed(1, 120).forEach(n -> w.tracker.issues.put("FACT-" + n,
                new JiraIssue("FACT-" + n, "Facture " + n, "Description " + n, Instant.EPOCH.plusSeconds(n))));
        ProposeProjectContext propose = new ProposeProjectContext(w.projects, w.docSources, w.docs, w.jira, w.tracker,
                w.markup, w.llm, w.policy, proposals, w.contextQuestions, w.clock);
        generations = new ContextGenerations(propose, w.projects, w.policy, proposals, queued::add, w.clock);
    }

    void scriptLlm() {
        w.llm.facts.add(new SourceFacts(List.of(new SourceFacts.Fact(Category.ACTOR, SECRET_TEXT, List.of(PAGE)))));
        w.llm.contexts.add(new ProjectContextDraft("## Acteurs\n- " + SECRET_TEXT + " [source : " + PAGE + "]", "",
                List.of(new ProjectContextDraft.Question(ContextSheet.TECHNICAL, "Quelle base de données ?", "Absente",
                        List.of("SQLite")))));
    }

    void runQueued() {
        List<Runnable> tasks = List.copyOf(queued);
        queued.clear();
        tasks.forEach(Runnable::run);
    }

    @Test
    void progressionDansLOrdrePuisPropositionEtErreursDetaillees() {
        scriptLlm();
        List<String> seen = new ArrayList<>();
        w.docs.onPage = () -> {
            Snapshot s = generations.current(project.id()).orElseThrow();
            seen.add(s.step() + " " + s.current() + " " + s.done() + "/" + s.total());
        };
        Snapshot started = generations.start(project.id());
        assertThat(started.status()).isEqualTo(Status.RUNNING);
        assertThat(started.startedAt()).isEqualTo(w.clock.now());
        runQueued();

        assertThat(seen).containsExactly("READING_DOCS " + PAGE + " 0/3", "READING_DOCS " + SITE + "tarifs.html 1/3",
                "READING_DOCS " + SITE + "recherche.html 2/3");
        assertThat(w.tracker.calls).containsSubsequence("recent FACT 0 50", "recent FACT 50 50", "recent FACT 100 50");

        Snapshot done = generations.current(project.id()).orElseThrow();
        assertThat(done.status()).isEqualTo(Status.SUCCEEDED);
        assertThat(done.step()).isEqualTo(Step.BUILDING);
        assertThat(done.done()).isEqualTo(1);
        assertThat(done.finishedAt()).isNotNull();
        assertThat(done.proposal()).isNotNull();
        assertThat(done.errors()).containsExactly(
                new GenerationError(Kind.PAGE, SITE + "tarifs.html", "HTTP_ERROR", 500, null),
                new GenerationError(Kind.PAGE, SITE + "recherche.html", "NOT_ANTORA", null, null));
    }

    @Test
    void ticketsSuivisParPagesDe50() {
        scriptLlm();
        List<String> seen = new ArrayList<>();
        GenerationProgress spy = new GenerationProgress() {
            @Override
            public void step(Step step, int total) {
                seen.add("step " + step + " " + total);
            }

            @Override
            public void advance(String current, int done, int total) {
                if (current != null && current.startsWith("FACT")) {
                    seen.add(current + " " + done + "/" + total);
                }
            }

            @Override
            public void error(GenerationError error) {
                // non vérifié ici
            }
        };
        new ProposeProjectContext(w.projects, w.docSources, w.docs, w.jira, w.tracker, w.markup, w.llm, w.policy, proposals,
                w.contextQuestions, w.clock).run(project.id(), spy);
        assertThat(seen).containsSubsequence("step READING_DOCS 0", "step READING_TICKETS 0", "FACT-71 50/120",
                "FACT-21 100/120", "FACT-1 120/120", "step SUMMARIZING 1", "step BUILDING 1");
    }

    @Test
    void uneSeuleGenerationParProjetEtConsentementVerifieAvant() {
        scriptLlm();
        generations.start(project.id());
        assertThatThrownBy(() -> generations.start(project.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.OPERATION_IN_PROGRESS));
        runQueued();
        scriptLlm();
        assertThat(generations.start(project.id()).status()).isEqualTo(Status.RUNNING);

        JiraWorld bare = new JiraWorld();
        bare.projects.save(project);
        ContextGenerations noConsent = new ContextGenerations(null, bare.projects, bare.policy, proposals, queued::add,
                bare.clock);
        assertThatThrownBy(() -> noConsent.start(project.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.CONSENT_REQUIRED));
        assertThatThrownBy(() -> generations.start(UUID.randomUUID())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND));
    }

    @Test
    void interruptionPendantLaLectureRienNEstPropose() {
        scriptLlm();
        w.docs.onPage = () -> generations.cancel(project.id());
        generations.start(project.id());
        runQueued();
        Snapshot s = generations.current(project.id()).orElseThrow();
        assertThat(s.status()).isEqualTo(Status.CANCELLED);
        assertThat(s.proposal()).isNull();
        assertThat(w.llm.calls()).isZero();
        assertThat(w.tracker.calls).isEmpty();
        assertThat(w.projects.find(project.id()).orElseThrow().contexts()).isEqualTo(ContextSheets.NONE);
    }

    @Test
    void echecsDetaillesSansContenu() {
        w.tracker.failure = IssueTrackerException.Kind.UNAUTHORIZED;
        w.llm.facts.add(new SourceFacts(List.of(new SourceFacts.Fact(Category.ACTOR, SECRET_TEXT,
                List.of("https://ailleurs.example/")))));
        w.llm.facts.add(new SourceFacts(List.of(new SourceFacts.Fact(Category.ACTOR, SECRET_TEXT, List.of(PAGE)))));
        w.llm.contexts.add(new ProjectContextDraft("- inventé [source : https://inventee.example/]", "", List.of()));
        w.llm.contexts.add(new ProjectContextDraft("- inventé [source : https://inventee.example/]", "", List.of()));
        w.llm.contexts.add(new ProjectContextDraft("- inventé [source : https://inventee.example/]", "", List.of()));
        generations.start(project.id());
        runQueued();

        Snapshot s = generations.current(project.id()).orElseThrow();
        assertThat(s.status()).isEqualTo(Status.FAILED);
        assertThat(s.errors()).extracting(GenerationError::kind).containsSubsequence(Kind.JIRA, Kind.CORRECTION,
                Kind.CORRECTION, Kind.CORRECTION, Kind.CORRECTION, Kind.FAILURE);
        assertThat(s.errors()).filteredOn(e -> e.kind() == Kind.JIRA).singleElement()
                .satisfies(e -> assertThat(e.reason()).isEqualTo("UNAUTHORIZED"));
        assertThat(s.errors().getLast().reason()).isEqualTo("LLM_INVALID_OUTPUT");
        assertThat(s.errors()).allSatisfy(e -> assertThat(String.valueOf(e.message())).doesNotContain(SECRET_TEXT));
    }

    @Test
    void generationTermineeGardee30Minutes() {
        scriptLlm();
        generations.start(project.id());
        runQueued();
        w.clock.advance(Duration.ofMinutes(29));
        assertThat(generations.current(project.id())).isPresent();
        w.clock.advance(Duration.ofMinutes(2));
        assertThat(generations.current(project.id())).isEmpty();
        generations.cancel(project.id());
    }

    @Test
    void revisionAvecLesReponsesSansRelireLesSources() {
        scriptLlm();
        generations.start(project.id());
        runQueued();
        ContextProposalStore.ContextProposal first = generations.current(project.id()).orElseThrow().proposal();
        ContextQuestion answered = new AnswerContextQuestion(proposals, w.contextQuestions, w.ids, w.clock)
                .run(project.id(), first.id(), first.questions().getFirst().id(), "SQLite", false);
        int reads = w.docs.reads.size();
        int jiraCalls = w.tracker.calls.size();
        int summaries = w.llm.summarizeRequests.size();

        w.llm.contexts.add(new ProjectContextDraft("## Acteurs\n- " + SECRET_TEXT + " [source : " + PAGE + "]",
                "## Contraintes techniques\n- Base SQLite. [source : " + answered.ref() + "]", List.of()));
        Snapshot revising = generations.revise(project.id(), first.id());
        assertThat(revising.status()).isEqualTo(Status.RUNNING);
        // l'ancienne proposition est remplacée
        assertThat(proposals.find(project.id(), first.id())).isEmpty();
        runQueued();

        Snapshot revised = generations.current(project.id()).orElseThrow();
        assertThat(revised.status()).isEqualTo(Status.SUCCEEDED);
        assertThat(revised.proposal().technical().markdown()).contains("[source : Q-1]");
        assertThat(revised.proposal().sources()).contains("Q-1");
        assertThat(w.docs.reads).hasSize(reads);
        assertThat(w.tracker.calls).hasSize(jiraCalls);
        assertThat(w.llm.summarizeRequests).hasSize(summaries);
        assertThat(w.llm.contextRequests.getLast().answers()).singleElement()
                .satisfies(a -> assertThat(a.answer()).isEqualTo("SQLite"));

        // la proposition révisée se révise à son tour ; l'ancienne, non
        assertThatThrownBy(() -> generations.revise(project.id(), first.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
        w.llm.contexts.add(new ProjectContextDraft("- " + SECRET_TEXT + " [source : " + PAGE + "]", "", List.of()));
        generations.revise(project.id(), revised.proposal().id());
        runQueued();
        assertThat(generations.current(project.id()).orElseThrow().status()).isEqualTo(Status.SUCCEEDED);
    }

    @Test
    void revisionImpossibleApresExpiration() {
        scriptLlm();
        generations.start(project.id());
        runQueued();
        ContextProposalStore.ContextProposal proposal = generations.current(project.id()).orElseThrow().proposal();
        w.clock.advance(Duration.ofMinutes(31));
        assertThatThrownBy(() -> generations.revise(project.id(), proposal.id())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
