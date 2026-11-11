package dev.specwriter.eval;

import dev.specwriter.application.session.AnswerQuestion;
import dev.specwriter.application.session.ConcludeQuestionnaire;
import dev.specwriter.application.session.SessionSnapshot;
import dev.specwriter.application.session.StartSession;
import dev.specwriter.application.specification.GenerateSpecification;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentRecord;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.scenario.ScenarioStructureValidator;
import dev.specwriter.domain.specification.SpecificationContent;
import dev.specwriter.infrastructure.llm.CopilotSdkGateway;
import dev.specwriter.infrastructure.llm.EvalCopilot;
import dev.specwriter.infrastructure.llm.LangChainLlmClient;
import dev.specwriter.support.FixedClock;
import dev.specwriter.support.InMemoryConsents;
import dev.specwriter.support.InMemoryProjects;
import dev.specwriter.support.InMemorySessions;
import dev.specwriter.support.InMemorySpecifications;
import dev.specwriter.support.SequentialIdGenerator;
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
 * se connecter avant par {@code copilot login}), {@code specwriter.copilot.cli-path}.
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
    public record CaseRun(String caseName, int run, List<Asked> questions, int scenarios, List<String> violations,
            String error) {

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
            List<DiscussionRuns.CaseRun> discussionRuns, List<PerimeterRuns.CaseRun> perimeterRuns) {
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
        Path home = Path.of(System.getProperty("eval.copilot-home", System.getProperty("user.home") + "/.copilot"));
        List<CaseRun> all = new ArrayList<>();
        List<ConversationRuns.CaseRun> conversations = new ArrayList<>();
        List<DiscussionRuns.CaseRun> discussions = new ArrayList<>();
        List<PerimeterRuns.CaseRun> perimeters = new ArrayList<>();
        String cliVersion;
        try (EvalCopilot copilot = new EvalCopilot(home, Optional.ofNullable(System.getProperty("specwriter.copilot.cli-path")))) {
            cliVersion = copilot.cliVersion();
            for (int run = 1; run <= runs; run++) {
                for (Path dir : cases()) {
                    all.add(runCase(copilot, model, dir, run));
                }
                conversations.addAll(ConversationRuns.run(copilot.conversationLlm(model), run));
                discussions.addAll(DiscussionRuns.run(copilot.conversationLlm(model), run));
                perimeters.addAll(PerimeterRuns.run(copilot.conversationLlm(model), run));
            }
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
                EvalBaseline.UNCHANGED_RECALL)) {
            conversation.put(metric, round(DiscussionRuns.score(discussions, metric, runs)));
        }
        for (String metric : List.of(EvalBaseline.PERIMETER_TERM_RECALL, EvalBaseline.PERIMETER_QUALIFICATION)) {
            conversation.put(metric, round(PerimeterRuns.score(perimeters, metric, runs)));
        }
        Result out = new Result(relevance, structure, readBaseline(), all, conversation, conversations, discussions,
                perimeters);
        write(out, model.orElse("défaut du compte"), cliVersion, runs);
        return out;
    }

    private static CaseRun runCase(EvalCopilot copilot, Optional<String> model, Path dir, int run) {
        String name = dir.getFileName().toString();
        String need = read(dir.resolve("need.md"));
        String reference = read(dir.resolve("expected.md"));
        InMemorySessions sessions = new InMemorySessions();
        InMemoryConsents consents = new InMemoryConsents();
        consents.record(new ConsentRecord(ConsentService.COPILOT, true, Instant.EPOCH));
        ConsentPolicy policy = new ConsentPolicy(consents);
        FixedClock clock = new FixedClock();
        SequentialIdGenerator ids = new SequentialIdGenerator();
        LangChainLlmClient llm = new LangChainLlmClient(copilot.chatModel(model));
        SimulatedUser user = new SimulatedUser(copilot.gateway(), model.orElse(null));

        List<Asked> asked = new ArrayList<>();
        int scenarios = 0;
        List<String> violations = List.of();
        String error = null;
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
            violations = ScenarioStructureValidator.validate(content).messages();
        } catch (DomainException e) {
            // une sortie inexploitable compte contre la qualité ; un compte non connecté, un quota
            // épuisé ou un CLI absent arrêtent l'évaluation, sans écrire de résultat
            if (!QUALITY_ERRORS.contains(e.code())) {
                throw e;
            }
            error = e.code().name();
        }
        List<Asked> judged = asked.isEmpty() ? asked : new QuestionJudge(copilot.gateway(), model.orElse(null)).judge(need, asked);
        return new CaseRun(name, run, judged, scenarios, violations, error);
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
