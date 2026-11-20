package fr.kawamind.eval;

import fr.kawamind.application.session.AnswerQuestion;
import fr.kawamind.application.session.ConcludeQuestionnaire;
import fr.kawamind.application.session.SessionSnapshot;
import fr.kawamind.application.session.StartSession;
import fr.kawamind.application.specification.GenerateSpecification;
import fr.kawamind.domain.consent.ConsentPolicy;
import fr.kawamind.domain.consent.ConsentRecord;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.question.Question;
import fr.kawamind.domain.scenario.ScenarioStructureValidator;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.infrastructure.llm.CopilotSdkGateway;
import fr.kawamind.domain.port.ConversationLlm;
import fr.kawamind.domain.port.LlmClient;
import fr.kawamind.domain.port.TaskLlm;
import fr.kawamind.domain.specification.UserStory;
import fr.kawamind.infrastructure.llm.CopilotRuntime;
import fr.kawamind.infrastructure.llm.EvalTools;
import io.quarkus.arc.Arc;
import fr.kawamind.support.FixedClock;
import fr.kawamind.support.InMemoryConsents;
import fr.kawamind.support.InMemoryProjects;
import fr.kawamind.support.InMemorySessions;
import fr.kawamind.support.InMemorySpecifications;
import fr.kawamind.support.SequentialIdGenerator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exécution de la suite d'évaluation (T137), partagée par ses deux classes de test : chaque besoin
 * du golden set passe par le vrai Copilot, {@code eval.runs} fois (3 par défaut). Un utilisateur
 * simulé répond aux questions d'après {@code expected.md}, un juge les classe selon la grille de
 * SC-004, puis la spécification générée passe le contrôle de structure (SC-002). Le résultat est
 * écrit dans {@code last-run.json}, à committer.
 *
 * <p>Propriétés : {@code eval.runs}, {@code eval.model} (sinon le modèle par défaut de
 * l'application, sinon celui du compte), {@code eval.copilot-home} ({@code ~/.copilot} par défaut :
 * se connecter avant par {@code copilot login}), {@code specwriter.copilot.cli-path}. Depuis la 014
 * (research R7), les appels passent par les beans de l'application ({@link EvalProfile}) : AI
 * Services, garde-fous et plafonds tels qu'ils sont livrés.
 */
public final class EvalRuns {

    static final Path REPO = Path.of("").toAbsolutePath().getParent();
    static final Path GOLDEN = Path.of("src/test/resources/golden");
    static final Path LAST_RUN = Path.of("src/test/resources/eval/last-run.json");
    static final Path BASELINE = Path.of("src/test/resources/eval/baseline.json");
    /** Au-delà, le questionnaire est conclu : borne le coût d'un besoin qui ne converge pas. */
    static final int MAX_BATCHES = 4;

    private static final Set<ErrorCode> QUALITY_ERRORS = EnumSet.of(ErrorCode.LLM_INVALID_OUTPUT,
            ErrorCode.CONTRADICTION_UNRESOLVED);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static Result result;
    private static RuntimeException failure;

    /** Une question posée, la réponse de l'utilisateur simulé et le verdict du juge. */
    public record Asked(String label, boolean contradiction, String answer, String verdict, String reason) {
    }

    /** Résultat d'un besoin pour une exécution. */
    /** {@code points} et {@code anchored} : hypothèses du modèle et écarts, et ceux qui sont rattachés (021). */
    public record CaseRun(String caseName, int run, List<Asked> questions, int scenarios, List<String> violations,
            String error, StoryJudge.Verdict story, int points, int anchored) {

        /** 015 (SC-007) : énoncé présent et jugé conforme ; une génération en échec n'en a pas. */
        boolean storyConform() {
            return error == null && story != null && StoryJudge.CONFORM.equals(story.verdict());
        }

        long irrelevant() {
            return questions.stream().filter(q -> !"PERTINENTE".equals(q.verdict())).count();
        }

        /** Scénarios conformes ; une génération en échec ou sans scénario compte comme un scénario non conforme. */
        int validScenarios() {
            return error != null || scenarios == 0 ? 0 : scenarios - invalidScenarios();
        }

        int countedScenarios() {
            return error != null || scenarios == 0 ? 1 : scenarios;
        }

        private int invalidScenarios() {
            return (int) violations.stream().map(v -> v.split(" : ", 2)[0]).distinct().count();
        }
    }

    public record Result(double questionRelevance, double scenarioStructure, EvalBaseline baseline, List<CaseRun> runs,
            Map<String, Double> conversation, List<ConversationRuns.CaseRun> conversationRuns,
            List<DiscussionRuns.CaseRun> discussionRuns, List<PerimeterRuns.CaseRun> perimeterRuns,
            List<PerimeterAnswerRuns.CaseRun> perimeterAnswerRuns, List<InjectionRuns.CaseRun> injectionRuns,
            List<TaskRuns.CaseRun> taskRuns, List<SourceRuns.CaseRun> sourceRuns) {
    }

    private EvalRuns() {
    }

    /** Une seule exécution par JVM : un échec est rejoué tel quel, sans relancer les appels Copilot. */
    public static synchronized Result result() {
        if (failure != null) {
            throw failure;
        }
        if (result == null) {
            try {
                result = run();
            } catch (RuntimeException e) {
                failure = e;
                throw e;
            }
        }
        return result;
    }

    /** Copilot indisponible un instant : jusqu'à 3 tentatives, espacées de 10 puis 20 s. */
    static <T> T retrying(java.util.function.Supplier<T> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (CopilotSdkGateway.GatewayException e) {
                if (attempt == 3 || e.kind() != CopilotSdkGateway.GatewayException.Kind.UNAVAILABLE) {
                    throw e;
                }
                try {
                    Thread.sleep(10_000L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private static Result run() {
        int runs = Integer.getInteger("eval.runs", 3);
        String configured = System.getProperty("eval.model", EvalFingerprint.defaultModel(REPO));
        Optional<String> model = Optional.of(configured).filter(m -> !m.isBlank());
        List<CaseRun> all = new ArrayList<>();
        List<ConversationRuns.CaseRun> conversations = new ArrayList<>();
        List<DiscussionRuns.CaseRun> discussions = new ArrayList<>();
        List<PerimeterRuns.CaseRun> perimeters = new ArrayList<>();
        List<PerimeterAnswerRuns.CaseRun> perimeterAnswers = new ArrayList<>();
        List<InjectionRuns.CaseRun> injections = new ArrayList<>();
        List<TaskRuns.CaseRun> tasks = new ArrayList<>();
        List<SourceRuns.CaseRun> sources = new ArrayList<>();
        // 014 (research R7) : le code livré, beans de l'application sur le vrai Copilot (EvalProfile)
        LlmClient llm = Arc.container().instance(LlmClient.class).get();
        ConversationLlm agent = Arc.container().instance(ConversationLlm.class).get();
        TaskLlm taskLlm = Arc.container().instance(TaskLlm.class).get();
        CopilotRuntime runtime = Arc.container().instance(CopilotRuntime.class).get();
        CopilotSdkGateway gateway = runtime;
        String cliVersion = EvalTools.cliVersion(Optional.ofNullable(System.getProperty("specwriter.copilot.cli-path")));
        for (int run = 1; run <= runs; run++) {
            for (Path dir : cases()) {
                all.add(runCase(llm, gateway, model, dir, run));
            }
            conversations.addAll(ConversationRuns.run(agent, run));
            discussions.addAll(DiscussionRuns.run(agent, run));
            perimeters.addAll(PerimeterRuns.run(agent, run));
            perimeterAnswers.addAll(PerimeterAnswerRuns.run(agent, run));
            injections.addAll(InjectionRuns.run(agent, runtime.emptyDirectory(), run));
            tasks.addAll(TaskRuns.run(taskLlm, gateway, model.orElse(null), run));
            sources.addAll(SourceRuns.run(agent, run));
        }
        List<double[]> perRun = new ArrayList<>();
        for (int run = 1; run <= runs; run++) {
            int r = run;
            List<CaseRun> of = all.stream().filter(c -> c.run() == r).toList();
            long asked = of.stream().mapToLong(c -> c.questions().size()).sum();
            long irrelevant = of.stream().mapToLong(CaseRun::irrelevant).sum();
            long scenarios = of.stream().mapToLong(CaseRun::countedScenarios).sum();
            long valid = of.stream().mapToLong(CaseRun::validScenarios).sum();
            perRun.add(new double[] {asked == 0 ? 1.0 : 1.0 - (double) irrelevant / asked, (double) valid / scenarios});
        }
        double relevance = round(perRun.stream().mapToDouble(s -> s[0]).average().orElse(0));
        double structure = round(perRun.stream().mapToDouble(s -> s[1]).average().orElse(0));
        Map<String, Double> conversation = new LinkedHashMap<>();
        conversation.put(EvalBaseline.CONVERSATION_CITATION,
                round(ConversationRuns.score(conversations, ConversationRuns.Kind.CITATION, runs)));
        conversation.put(EvalBaseline.CONVERSATION_ABSTENTION,
                round(ConversationRuns.score(conversations, ConversationRuns.Kind.ABSTENTION, runs)));
        conversation.put(EvalBaseline.INCONSISTENCY_RECALL,
                round(ConversationRuns.score(conversations, ConversationRuns.Kind.INCONSISTENCY, runs)));
        for (String metric : List.of(EvalBaseline.DISCUSSION_CITATION, EvalBaseline.DISCUSSION_INCONSISTENCY,
                EvalBaseline.REVISION_COMPLETENESS, EvalBaseline.REVISION_PRESERVATION, EvalBaseline.REVISION_STRUCTURE,
                EvalBaseline.UNCHANGED_RECALL, EvalBaseline.CHANGES_RECALL, EvalBaseline.CHANGES_PRECISION,
                EvalBaseline.DECIDED_REMOVED)) {
            conversation.put(metric, round(DiscussionRuns.score(discussions, metric, runs)));
        }
        for (String metric : List.of(EvalBaseline.PERIMETER_TERM_RECALL, EvalBaseline.PERIMETER_QUALIFICATION)) {
            conversation.put(metric, round(PerimeterRuns.score(perimeters, metric, runs)));
        }
        conversation.put(EvalBaseline.PERIMETER_ANSWER_FOCUS,
                round(PerimeterAnswerRuns.score(perimeterAnswers, runs)));
        conversation.put(EvalBaseline.INJECTION_RESISTANCE, round(InjectionRuns.score(injections, runs)));
        for (String metric : List.of(EvalBaseline.TASK_BOUNDS, EvalBaseline.TASK_BASIS_EXACT,
                EvalBaseline.TASK_MODULE_COVERAGE, EvalBaseline.TASK_VERIFIABLE)) {
            conversation.put(metric, round(TaskRuns.score(tasks, metric, runs)));
        }
        conversation.put(EvalBaseline.STORY_STATEMENT, round(storyScore(all, runs)));
        conversation.put(EvalBaseline.ANCHORED_RATE, round(anchoredRate(all)));
        conversation.put(EvalBaseline.SOURCE_CONFLICT_FLAGGED, round(SourceRuns.conflictScore(sources, runs)));
        conversation.put(EvalBaseline.REFERENCE_RETAINED, round(SourceRuns.retainedScore(sources, runs)));
        Result out = new Result(relevance, structure, readBaseline(), all, conversation, conversations, discussions,
                perimeters, perimeterAnswers, injections, tasks, sources);
        write(out, model.orElse("défaut du compte"), cliVersion, runs);
        return out;
    }

    /** Part des générations dont l'énoncé de story est conforme, moyenne des exécutions. */
    static double storyScore(List<CaseRun> all, int runs) {
        double sum = 0;
        for (int run = 1; run <= runs; run++) {
            int r = run;
            List<CaseRun> of = all.stream().filter(c -> c.run() == r).toList();
            sum += of.isEmpty() ? 1.0 : (double) of.stream().filter(CaseRun::storyConform).count() / of.size();
        }
        return sum / runs;
    }

    private static CaseRun runCase(LlmClient llm, CopilotSdkGateway gateway, Optional<String> model, Path dir,
            int run) {
        String name = dir.getFileName().toString();
        String need = read(dir.resolve("need.md"));
        String reference = read(dir.resolve("expected.md"));
        InMemorySessions sessions = new InMemorySessions();
        InMemoryConsents consents = new InMemoryConsents();
        consents.record(new ConsentRecord(ConsentService.COPILOT, true, Instant.EPOCH));
        ConsentPolicy policy = new ConsentPolicy(consents);
        FixedClock clock = new FixedClock();
        SequentialIdGenerator ids = new SequentialIdGenerator();
        SimulatedUser user = new SimulatedUser(gateway, model.orElse(null));

        List<Asked> asked = new ArrayList<>();
        int scenarios = 0;
        List<String> violations = List.of();
        String error = null;
        UserStory story = null;
        int points = 0;
        int anchored = 0;
        try {
            SessionSnapshot s = new StartSession(sessions, new InMemoryProjects(), llm, policy, clock, ids)
                    .run(need, null);
            AnswerQuestion answer = new AnswerQuestion(sessions, new InMemoryProjects(), llm, policy, clock, ids);
            // au-delà de MAX_BATCHES, seules les contradictions sont encore tranchées : elles bloquent la génération
            while (!s.questionnaire().currentBatch().isEmpty()
                    && (s.questionnaire().currentBatch().getFirst().batch() <= MAX_BATCHES
                            || s.questionnaire().currentBatch().getFirst().isContradiction())) {
                Question q = s.questionnaire().currentBatch().getFirst();
                SimulatedUser.Reply reply = user.answer(need, reference, q);
                asked.add(new Asked(q.label(), q.isContradiction(), reply.skip() ? "(passée)" : reply.answer(), null, null));
                s = answer.run(s.session().id(), q.id(), reply.skip() ? "" : reply.answer(), reply.skip());
            }
            new ConcludeQuestionnaire(sessions, clock).run(s.session().id());
            SpecificationContent content = new GenerateSpecification(sessions, new InMemoryProjects(), new InMemorySpecifications(), llm, policy,
                    clock, ids, "eval").run(s.session().id()).version().content();
            scenarios = content.scenarios().size();
            story = content.story();
            violations = ScenarioStructureValidator.validate(content).messages();
            // 021 (R1) : les hypothèses ajoutées d'office pour une question passée n'ont pas d'élément
            var inferred = content.assumptions().stream()
                    .filter(a -> a.origin() == fr.kawamind.domain.specification.Assumption.Origin.TOOL_INFERENCE).toList();
            points = inferred.size() + content.sourceDeviations().size();
            anchored = (int) (inferred.stream().filter(a -> a.anchor() != null).count()
                    + content.sourceDeviations().stream().filter(d -> d.anchor() != null).count());
        } catch (DomainException e) {
            // une sortie inexploitable compte contre la qualité ; un compte non connecté, un quota
            // épuisé ou un CLI absent arrêtent l'évaluation, sans écrire de résultat
            if (!QUALITY_ERRORS.contains(e.code())) {
                throw e;
            }
            error = e.code().name();
        }
        List<Asked> judged = asked.isEmpty() ? asked : new QuestionJudge(gateway, model.orElse(null)).judge(need, asked);
        StoryJudge.Verdict verdict = error != null ? null
                : new StoryJudge(gateway, model.orElse(null)).judge(need, asked, story);
        return new CaseRun(name, run, judged, scenarios, violations, error, verdict, points, anchored);
    }

    /** 021 (US4) : part des hypothèses et écarts rattachés à un élément ; 1 sans aucun point. */
    static double anchoredRate(List<CaseRun> all) {
        int points = all.stream().mapToInt(CaseRun::points).sum();
        int anchored = all.stream().mapToInt(CaseRun::anchored).sum();
        return points == 0 ? 1.0 : (double) anchored / points;
    }

    static List<Path> cases() {
        try (Stream<Path> list = Files.list(GOLDEN)) {
            return list.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static EvalBaseline readBaseline() {
        JsonNode node = JSON.readTree(read(BASELINE));
        return new EvalBaseline(node.path("tolerance").asDouble(0), metrics(node.path("minimum")), metrics(node.path("scores")));
    }

    private static Map<String, Double> metrics(JsonNode node) {
        Map<String, Double> out = new LinkedHashMap<>();
        node.properties().forEach(e -> out.put(e.getKey(), e.getValue().asDouble()));
        return out;
    }

    private static void write(Result r, String model, String cliVersion, int runs) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        out.put("fingerprint", EvalFingerprint.compute(REPO));
        out.put("model", model);
        out.put("copilotSdk", EvalFingerprint.copilotSdk(REPO));
        out.put("copilotCli", cliVersion);
        out.put("runs", runs);
        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put(EvalBaseline.SCENARIO_STRUCTURE, r.scenarioStructure());
        scores.put(EvalBaseline.QUESTION_RELEVANCE, r.questionRelevance());
        scores.putAll(r.conversation());
        out.put("scores", scores);
        List<Map<String, Object>> details = new ArrayList<>();
        for (CaseRun c : r.runs()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("case", c.caseName());
            d.put("run", c.run());
            d.put("questions", c.questions().size());
            d.put("irrelevant", c.questions().stream().filter(q -> !"PERTINENTE".equals(q.verdict()))
                    .map(q -> Map.of("question", q.label(), "verdict", q.verdict(), "reason", q.reason())).toList());
            d.put("scenarios", c.scenarios());
            d.put("structureViolations", c.violations());
            if (c.error() != null) {
                d.put("error", c.error());
            }
            if (c.story() != null && !c.storyConform()) {
                d.put("story", Map.of("verdict", c.story().verdict(), "reason", c.story().reason()));
            }
            details.add(d);
        }
        out.put("details", details);
        out.put("conversation", r.conversationRuns().stream().map(c -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("case", c.caseName());
            d.put("run", c.run());
            d.put("passed", c.passed());
            d.put("state", c.state());
            d.put("cited", c.cited());
            if (!c.passed()) {
                d.put("excerpt", c.excerpt());
            }
            if (c.error() != null) {
                d.put("error", c.error());
            }
            return d;
        }).toList());
        out.put("discussion", r.discussionRuns().stream().map(c -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("case", c.caseName());
            d.put("run", c.run());
            d.put("passed", c.passed());
            d.put("checks", c.checks());
            d.put("outcome", c.outcome());
            if (!c.cited().isEmpty()) {
                d.put("cited", c.cited());
            }
            if (!c.passed()) {
                d.put("excerpt", c.excerpt());
            }
            return d;
        }).toList());
        out.put("perimeter", r.perimeterRuns().stream().map(c -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("case", c.caseName());
            d.put("run", c.run());
            d.put("terms", c.terms());
            d.put("recall", round(c.recall()));
            d.put("agreement", round(c.agreement()));
            d.put("found", c.found());
            if (c.error() != null) {
                d.put("error", c.error());
            }
            return d;
        }).toList());
        out.put("perimeterAnswer", r.perimeterAnswerRuns().stream().map(c -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("case", c.caseName());
            d.put("run", c.run());
            d.put("passed", c.passed());
            d.put("impacted", c.impacted());
            d.put("notImpacted", c.notImpacted());
            d.put("cited", c.cited());
            if (!c.passed()) {
                d.put("excerpt", c.excerpt());
            }
            if (c.error() != null) {
                d.put("error", c.error());
            }
            return d;
        }).toList());
        out.put("injection", r.injectionRuns().stream().map(c -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("case", c.caseName());
            d.put("run", c.run());
            d.put("passed", c.passed());
            d.put("state", c.state());
            if (!c.passed()) {
                d.put("secretLeaked", c.secretLeaked());
                d.put("promptLeaked", c.promptLeaked());
                d.put("excerpt", c.excerpt());
            }
            return d;
        }).toList());
        out.put("sources", r.sourceRuns().stream().map(c -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("case", c.caseName());
            d.put("run", c.run());
            d.put("passed", c.passed());
            d.put("referenceRetained", c.referenceRetained());
            d.put("state", c.state());
            d.put("cited", c.cited());
            d.put("excerpt", c.text().length() <= ConversationRuns.EXCERPT ? c.text()
                    : c.text().substring(0, ConversationRuns.EXCERPT) + "…");
            if (c.error() != null) {
                d.put("error", c.error());
            }
            return d;
        }).toList());
        out.put("tasks", r.taskRuns().stream().map(c -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("case", c.caseName());
            d.put("run", c.run());
            d.put("bounds", c.bounds());
            d.put("basisExact", round(c.basisExact()));
            if (c.moduleCoverage() != null) {
                d.put("moduleCoverage", round(c.moduleCoverage()));
            }
            d.put("verifiable", round(c.verifiable()));
            d.put("tasks", java.util.stream.IntStream.range(0, c.tasks().size()).mapToObj(i -> Map.of(
                    "title", c.tasks().get(i).title(), "basis", String.valueOf(c.tasks().get(i).basis().ref()),
                    "verdict", i < c.verdicts().size() ? c.verdicts().get(i) : "")).toList());
            if (c.error() != null) {
                d.put("error", c.error());
            }
            return d;
        }).toList());
        try {
            Files.writeString(LAST_RUN, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(out) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static double round(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }

    static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
