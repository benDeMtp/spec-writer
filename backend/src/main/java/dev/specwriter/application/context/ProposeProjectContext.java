package dev.specwriter.application.context;

import java.util.Map;
import java.util.HashMap;
import dev.specwriter.domain.project.ContextSheet;
import dev.specwriter.domain.project.ContextQuestion;
import dev.specwriter.domain.port.ContextQuestionRepository;
import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.JiraIssue;
import dev.specwriter.domain.llm.BuildContextRequest;
import dev.specwriter.domain.llm.ProjectContextDraft;
import dev.specwriter.domain.llm.SourceFacts;
import dev.specwriter.domain.llm.SummarizeSourcesRequest;
import dev.specwriter.domain.llm.SummarizeSourcesRequest.Source;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.DocSourceRepository;
import dev.specwriter.domain.port.DocumentationReader;
import dev.specwriter.domain.port.DocumentationReader.Unread;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.WikiMarkup;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import dev.specwriter.domain.session.Language;
import dev.specwriter.domain.session.LanguageDetector;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Propose une fiche de contexte à partir de la documentation et des tickets du projet (FR-013a,
 * research.md R4 de 002). Deux temps : des faits sourcés par lots d'environ 40 000 caractères, puis
 * la fiche. Chaque référence citée doit exister parmi les sources lues ; sinon une correction est
 * demandée, 3 essais au plus. Rien n'est enregistré : la fiche est une proposition. Les pages et les
 * tickets ne sont jamais journalisés (FR-012).
 */
public class ProposeProjectContext {

    public static final int BATCH_CHARS = 40_000;
    static final int MAX_ATTEMPTS = 3;
    static final int MAX_QUESTIONS = 10;
    private static final Pattern CITATION = Pattern.compile("\\[source\\s*:\\s*([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    private final ProjectRepository projects;
    private final DocSourceRepository docSources;
    private final DocumentationReader reader;
    private final JiraAccess jira;
    private final IssueTracker tracker;
    private final WikiMarkup markup;
    private final LlmClient llm;
    private final ConsentPolicy consent;
    private final ContextProposalStore proposals;
    private final ContextQuestionRepository questions;
    private final ClockPort clock;

    public ProposeProjectContext(ProjectRepository projects, DocSourceRepository docSources, DocumentationReader reader,
            JiraAccess jira, IssueTracker tracker, WikiMarkup markup, LlmClient llm, ConsentPolicy consent,
            ContextProposalStore proposals, ContextQuestionRepository questions, ClockPort clock) {
        this.projects = projects;
        this.docSources = docSources;
        this.reader = reader;
        this.jira = jira;
        this.tracker = tracker;
        this.markup = markup;
        this.llm = llm;
        this.consent = consent;
        this.proposals = proposals;
        this.questions = questions;
        this.clock = clock;
    }

    /**
     * Ce qui a été lu et extrait : la base d'une proposition, gardée par la génération pour une
     * révision après les réponses, sans relire les sources (research.md R16).
     */
    public record Basis(Language language, List<SourceFacts.Fact> facts, Set<String> sourceRefs, List<Unread> unread) {

        public Basis {
            facts = List.copyOf(facts);
            sourceRefs = Set.copyOf(sourceRefs);
            unread = List.copyOf(unread);
        }
    }

    public ContextProposalStore.ContextProposal run(UUID projectId) {
        return run(projectId, GenerationProgress.NONE);
    }

    /**
     * @throws GenerationProgress.Cancelled si l'interruption est constatée avant une page, une page
     *         de tickets ou un appel au LLM
     */
    public ContextProposalStore.ContextProposal run(UUID projectId, GenerationProgress progress) {
        return draft(projectId, prepare(projectId, progress), progress);
    }

    /** Lit les sources et les tickets, puis en extrait les faits. */
    public Basis prepare(UUID projectId, GenerationProgress progress) {
        Project project = projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        consent.requireGranted(ConsentService.COPILOT);

        List<Source> sources = new ArrayList<>();
        List<Unread> unread = new ArrayList<>();
        List<DocSource> declared = docSources.list(projectId);
        if (!declared.isEmpty()) {
            progress.step(GenerationProgress.Step.READING_DOCS, 0);
        }
        int[] offset = {0};
        for (DocSource source : declared) {
            DocumentationReader.Result read = reader.read(source, new DocumentationReader.Listener() {
                @Override
                public void reading(String location, int done, int total) {
                    progress.advance(location, offset[0] + done, total == 0 ? 0 : offset[0] + total);
                }

                @Override
                public boolean cancelled() {
                    return progress.cancelled();
                }
            });
            requireNotCancelled(progress);
            offset[0] += read.pages().size() + read.unread().size();
            read.pages().forEach(p -> sources.add(new Source(p.ref(), p.title(), p.text())));
            unread.addAll(read.unread());
            read.unread().forEach(u -> progress.error(GenerationError.of(u, source.location())));
            docSources.save(source.read(clock.now(), new DocSource.ReadSummary(read.pages().size(),
                    (int) read.unread().stream().filter(ProposeProjectContext::inError).count(), read.limitReached())));
        }
        ProjectTickets.Read tickets = ProjectTickets.read(jira, tracker, project, progress);
        if (tickets.failure() != null) {
            progress.error(new GenerationError(GenerationError.Kind.JIRA, "Jira " + tickets.target().projectKey(),
                    tickets.failure().kind().name(), null, tickets.failure().errorCode().defaultMessage()));
        }
        for (JiraIssue issue : tickets.issues()) {
            sources.add(new Source(issue.key(), issue.summary(),
                    markup.toMarkdown(issue.description(), tickets.target().issueUrl(issue.key()))));
        }
        tickets.unread().ifPresent(unread::add);
        if (sources.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Aucune page ni aucun ticket n'a pu être lu : ajoutez une source de documentation, ou saisissez le "
                            + "contexte à la main.");
        }

        Language language = LanguageDetector.detect(sample(sources));
        Set<String> known = sources.stream().map(Source::ref).collect(Collectors.toCollection(LinkedHashSet::new));
        List<SourceFacts.Fact> facts = new ArrayList<>();
        List<List<Source>> batches = batches(sources);
        progress.step(GenerationProgress.Step.SUMMARIZING, batches.size());
        for (int i = 0; i < batches.size(); i++) {
            progress.advance(null, i, batches.size());
            facts.addAll(summarize(language, batches.get(i), progress));
        }
        progress.advance(null, batches.size(), batches.size());
        return new Basis(language, facts, known, unread);
    }

    /**
     * Rédige les deux fiches et les questions de clarification à partir de la base, des fiches
     * actuelles et des réponses déjà données (FR-013a, FR-013h, FR-013i).
     */
    public ContextProposalStore.ContextProposal draft(UUID projectId, Basis basis, GenerationProgress progress) {
        Project project = projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        consent.requireGranted(ConsentService.COPILOT);
        List<ContextQuestion> asked = questions.list(projectId);
        List<BuildContextRequest.Answer> answers = asked.stream().filter(q -> q.state() == ContextQuestion.State.ANSWERED)
                .map(q -> new BuildContextRequest.Answer(q.ref(), q.sheet(), q.label(), q.answer())).toList();
        List<BuildContextRequest.Skipped> skipped = asked.stream().filter(q -> q.state() == ContextQuestion.State.SKIPPED)
                .map(q -> new BuildContextRequest.Skipped(q.sheet(), q.label())).toList();
        Set<String> known = new LinkedHashSet<>(basis.sourceRefs());
        answers.forEach(a -> known.add(a.ref()));
        progress.step(GenerationProgress.Step.BUILDING, 1);
        BuildContextRequest request = new BuildContextRequest(basis.language(), basis.facts(),
                project.context(ContextSheet.FUNCTIONAL).markdown(), project.context(ContextSheet.TECHNICAL).markdown(),
                answers, skipped, "");
        ProjectContextDraft draft = build(request, known, asked, progress);
        progress.advance(null, 1, 1);
        requireNotCancelled(progress);
        ProjectContextDraft bounded = new ProjectContextDraft(draft.functional(), draft.technical(),
                draft.questions().stream().limit(MAX_QUESTIONS).toList());
        return proposals.propose(project, bounded, cited(draft.functional() + "\n" + draft.technical()), basis.unread());
    }

    /** Lecture en échec ; une page hors Antora est écartée, pas en erreur, et la limite est signalée à part. */
    private static boolean inError(Unread u) {
        return u.reason() != DocumentationReader.UnreadReason.LIMIT_REACHED
                && u.reason() != DocumentationReader.UnreadReason.NOT_ANTORA;
    }

    /** Lots d'au plus {@link #BATCH_CHARS} caractères ; une source plus longue est tronquée. */
    static List<List<Source>> batches(List<Source> sources) {
        List<List<Source>> out = new ArrayList<>();
        List<Source> current = new ArrayList<>();
        int size = 0;
        for (Source s : sources) {
            String text = s.text() == null ? "" : s.text();
            Source bounded = text.length() > BATCH_CHARS ? new Source(s.ref(), s.title(), text.substring(0, BATCH_CHARS)) : s;
            int length = bounded.text().length();
            if (!current.isEmpty() && size + length > BATCH_CHARS) {
                out.add(current);
                current = new ArrayList<>();
                size = 0;
            }
            current.add(bounded);
            size += length;
        }
        if (!current.isEmpty()) {
            out.add(current);
        }
        return out;
    }

    private List<SourceFacts.Fact> summarize(Language language, List<Source> batch, GenerationProgress progress) {
        Set<String> refs = batch.stream().map(Source::ref).collect(Collectors.toSet());
        SummarizeSourcesRequest request = new SummarizeSourcesRequest(language, batch, "");
        return retry(progress, "extraction des faits",
                correction -> llm.summarizeProjectSources(request.withCorrection(correction)).facts(), facts -> {
            List<String> problems = new ArrayList<>();
            for (SourceFacts.Fact f : facts) {
                if (f.refs().isEmpty()) {
                    // le fait lui-même n'est pas cité : le motif d'une correction est affiché (FR-013g)
                    problems.add("un fait de la catégorie " + f.category() + " ne cite aucune source du lot");
                }
                f.refs().stream().filter(r -> !refs.contains(r))
                        .forEach(r -> problems.add("la source " + r + " ne fait pas partie du lot"));
            }
            return problems;
        });
    }

    private ProjectContextDraft build(BuildContextRequest request, Set<String> known, List<ContextQuestion> asked,
            GenerationProgress progress) {
        Map<String, ContextQuestion> askedByLabel = new HashMap<>();
        asked.forEach(q -> askedByLabel.put(normalized(q.label()), q));
        return retry(progress, "rédaction des fiches", correction -> llm.buildProjectContext(request.withCorrection(correction)),
                (ProjectContextDraft d) -> {
                    List<String> problems = new ArrayList<>();
                    if (d.functional().isEmpty() && d.technical().isEmpty()) {
                        problems.add("les deux fiches sont vides");
                    }
                    for (ContextSheet sheet : ContextSheet.values()) {
                        String label = sheet == ContextSheet.FUNCTIONAL ? "fonctionnelle" : "technique";
                        if (d.get(sheet).length() > ProjectContext.MAX_LENGTH) {
                            problems.add("la fiche " + label + " dépasse 20 000 caractères : raccourcis-la");
                        }
                        cited(d.get(sheet)).stream().filter(r -> !known.contains(r)).forEach(r -> problems.add(
                                "la référence " + r + " de la fiche " + label + " ne correspond à aucune source lue"));
                    }
                    for (ProjectContextDraft.Question q : d.questions()) {
                        ContextQuestion already = askedByLabel.get(normalized(q.label()));
                        if (already != null) {
                            problems.add("la question « " + q.label() + " » a déjà été posée (" + already.ref()
                                    + ") : ne la repose pas");
                        }
                    }
                    return problems;
                });
    }

    private static String normalized(String text) {
        return text == null ? "" : text.strip().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Appel au LLM, puis jusqu'à 2 nouvelles demandes avec les écarts constatés, signalés (FR-013g). */
    private static <T> T retry(GenerationProgress progress, String operation, Function<String, T> call,
            Function<T, List<String>> check) {
        String correction = "";
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            requireNotCancelled(progress);
            T result = call.apply(correction);
            List<String> problems = check.apply(result);
            if (problems.isEmpty()) {
                return result;
            }
            correction = String.join("\n", problems);
            progress.error(new GenerationError(GenerationError.Kind.CORRECTION, null, operation, null,
                    "Essai " + attempt + " sur " + MAX_ATTEMPTS + " : " + String.join(" ; ", problems)));
        }
        throw new DomainException(ErrorCode.LLM_INVALID_OUTPUT);
    }

    private static void requireNotCancelled(GenerationProgress progress) {
        if (progress.cancelled()) {
            throw new GenerationProgress.Cancelled();
        }
    }

    /** Références citées par la fiche, dans leur ordre d'apparition. */
    static List<String> cited(String markdown) {
        Set<String> refs = new LinkedHashSet<>();
        Matcher m = CITATION.matcher(markdown);
        while (m.find()) {
            for (String ref : m.group(1).split(",")) {
                if (!ref.isBlank()) {
                    refs.add(ref.strip());
                }
            }
        }
        return List.copyOf(refs);
    }

    private static String sample(List<Source> sources) {
        StringBuilder out = new StringBuilder();
        for (Source s : sources) {
            if (out.length() > 4_000) {
                break;
            }
            out.append(s.text()).append('\n');
        }
        return out.toString();
    }
}
