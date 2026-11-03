package dev.specwriter.application.context;

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
    private final ClockPort clock;

    public ProposeProjectContext(ProjectRepository projects, DocSourceRepository docSources, DocumentationReader reader,
            JiraAccess jira, IssueTracker tracker, WikiMarkup markup, LlmClient llm, ConsentPolicy consent,
            ContextProposalStore proposals, ClockPort clock) {
        this.projects = projects;
        this.docSources = docSources;
        this.reader = reader;
        this.jira = jira;
        this.tracker = tracker;
        this.markup = markup;
        this.llm = llm;
        this.consent = consent;
        this.proposals = proposals;
        this.clock = clock;
    }

    public ContextProposalStore.ContextProposal run(UUID projectId) {
        Project project = projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        consent.requireGranted(ConsentService.COPILOT);

        List<Source> sources = new ArrayList<>();
        List<Unread> unread = new ArrayList<>();
        for (DocSource source : docSources.list(projectId)) {
            DocumentationReader.Result read = reader.read(source);
            read.pages().forEach(p -> sources.add(new Source(p.ref(), p.title(), p.text())));
            unread.addAll(read.unread());
            docSources.save(source.read(clock.now(), new DocSource.ReadSummary(read.pages().size(),
                    (int) read.unread().stream().filter(ProposeProjectContext::inError).count(), read.limitReached())));
        }
        ProjectTickets.Read tickets = ProjectTickets.read(jira, tracker, project);
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
        for (List<Source> batch : batches(sources)) {
            facts.addAll(summarize(language, batch));
        }
        String markdown = build(language, facts, project.context(), known);
        return proposals.propose(project, markdown, cited(markdown), unread);
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

    private List<SourceFacts.Fact> summarize(Language language, List<Source> batch) {
        Set<String> refs = batch.stream().map(Source::ref).collect(Collectors.toSet());
        SummarizeSourcesRequest request = new SummarizeSourcesRequest(language, batch, "");
        return retry(correction -> llm.summarizeProjectSources(request.withCorrection(correction)).facts(), facts -> {
            List<String> problems = new ArrayList<>();
            for (SourceFacts.Fact f : facts) {
                if (f.refs().isEmpty()) {
                    problems.add("le fait « " + f.statement() + " » ne cite aucune source du lot");
                }
                f.refs().stream().filter(r -> !refs.contains(r))
                        .forEach(r -> problems.add("la source " + r + " ne fait pas partie du lot"));
            }
            return problems;
        });
    }

    private String build(Language language, List<SourceFacts.Fact> facts, ProjectContext current, Set<String> known) {
        BuildContextRequest request = new BuildContextRequest(language, facts, current.markdown(), "");
        return retry(correction -> llm.buildProjectContext(request.withCorrection(correction)), (ProjectContextDraft d) -> {
            List<String> problems = new ArrayList<>();
            String markdown = d.markdown() == null ? "" : d.markdown().strip();
            if (markdown.isEmpty()) {
                problems.add("la fiche est vide");
            }
            if (markdown.length() > ProjectContext.MAX_LENGTH) {
                problems.add("la fiche dépasse 20 000 caractères : raccourcis-la");
            }
            cited(markdown).stream().filter(r -> !known.contains(r))
                    .forEach(r -> problems.add("la référence " + r + " ne correspond à aucune source lue"));
            return problems;
        }).markdown().strip();
    }

    /** Appel au LLM, puis jusqu'à 2 nouvelles demandes avec les écarts constatés. */
    private static <T> T retry(Function<String, T> call, Function<T, List<String>> check) {
        String correction = "";
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            T result = call.apply(correction);
            List<String> problems = check.apply(result);
            if (problems.isEmpty()) {
                return result;
            }
            correction = String.join("\n", problems);
        }
        throw new DomainException(ErrorCode.LLM_INVALID_OUTPUT);
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
