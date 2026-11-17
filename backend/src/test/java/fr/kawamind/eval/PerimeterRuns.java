package fr.kawamind.eval;

import fr.kawamind.application.sources.AnalyzePerimeter;
import fr.kawamind.domain.conversation.SourceKind;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.port.ConversationLlm;
import fr.kawamind.domain.port.TurnTools;
import fr.kawamind.domain.project.ExclusionPatterns;
import fr.kawamind.domain.session.Language;
import fr.kawamind.domain.sources.PerimeterAnalysis;
import fr.kawamind.domain.sources.PerimeterModule;
import fr.kawamind.domain.sources.SourceRoot;
import fr.kawamind.infrastructure.sources.DemoTree;
import fr.kawamind.infrastructure.sources.LocalSourceTree;
import fr.kawamind.support.FixedClock;
import fr.kawamind.support.InMemorySourceRoots;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Volet périmètre de l'évaluation (013, T034) : sur l'arbre de démonstration et un second arbre
 * généré, l'agent appelle son outil d'analyse avec ses termes (014), puis qualifie les modules recensés. Mesure le
 * rappel des modules attendus par les termes du vrai plan, et l'accord des qualifications fermes.
 * Attendus : {@code IMPACTED} ou {@code NOT_IMPACTED} (qualification exigée), {@code ANY} (le
 * module doit être trouvé), {@code NOT_IMPACTED_IF_FOUND} (hors rappel ; non impacté s'il est trouvé).
 */
final class PerimeterRuns {

    static final Path CASES = Path.of("src/test/resources/eval/perimeter/cases.json");
    static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000013");
    private static final String ANY = "ANY";
    private static final String IF_FOUND = "NOT_IMPACTED_IF_FOUND";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Résultat d'un cas : termes proposés, modules trouvés et qualifications obtenues. */
    record CaseRun(String caseName, int run, List<String> terms, Map<String, String> expected,
            Map<String, String> found, String error) {

        /** Part des modules attendus (hors {@code NOT_IMPACTED_IF_FOUND}) que les termes ont trouvés. */
        double recall() {
            List<String> wanted = expected.entrySet().stream().filter(e -> !IF_FOUND.equals(e.getValue()))
                    .map(Map.Entry::getKey).toList();
            return wanted.isEmpty() ? 1.0 : (double) wanted.stream().filter(found::containsKey).count() / wanted.size();
        }

        /** Qualifications fermes respectées ; un module attendu non trouvé compte comme un désaccord. */
        double agreement() {
            int checked = 0;
            int agreed = 0;
            for (Map.Entry<String, String> e : expected.entrySet()) {
                String got = found.get(e.getKey());
                if (ANY.equals(e.getValue()) || IF_FOUND.equals(e.getValue()) && got == null) {
                    continue;
                }
                String wanted = IF_FOUND.equals(e.getValue()) ? "NOT_IMPACTED" : e.getValue();
                checked++;
                agreed += wanted.equals(got) ? 1 : 0;
            }
            return checked == 0 ? 1.0 : (double) agreed / checked;
        }
    }

    private PerimeterRuns() {
    }

    static List<CaseRun> run(ConversationLlm llm, int run) {
        JsonNode root = JSON.readTree(EvalRuns.read(CASES));
        Path work = temp();
        try {
            Map<String, Path> trees = new LinkedHashMap<>();
            trees.put("demo-rv", DemoTree.copy(work));
            root.path("trees").properties().forEach(t -> trees.put(t.getKey(), write(work.resolve(t.getKey()), t.getValue())));
            List<CaseRun> out = new ArrayList<>();
            for (JsonNode c : root.path("cases")) {
                Map<String, String> expected = new LinkedHashMap<>();
                c.path("expected").properties().forEach(e -> expected.put(e.getKey(), e.getValue().asString()));
                out.add(analyze(llm, root.path("sheet").asString(), trees.get(c.path("tree").asString()),
                        c.path("name").asString(), c.path("question").asString(), expected, run));
            }
            return out;
        } finally {
            delete(work);
        }
    }

    /**
     * Outils d'un tour d'évaluation (014) : la recherche ne trouve rien ; l'analyse de périmètre
     * demandée par l'agent est faite et qualifiée comme dans l'application, et gardée.
     */
    private static final class PerimeterTools implements TurnTools {
        private final AnalyzePerimeter analyze;
        private final String question;
        List<String> terms;
        boolean invalid;
        AnalyzePerimeter.Qualified qualified;
        int steps;

        PerimeterTools(AnalyzePerimeter analyze, String question) {
            this.analyze = analyze;
            this.question = question;
        }

        void reset() {
            terms = null;
            invalid = false;
            qualified = null;
            steps = 0;
        }

        @Override
        public Found search(String query, Set<SourceKind> kinds) {
            steps++;
            return new Found(List.of(), List.of());
        }

        @Override
        public PerimeterOutcome perimeter(List<String> wanted) {
            steps++;
            terms = List.copyOf(wanted);
            PerimeterAnalysis analysis;
            try {
                analysis = analyze.analyze(PROJECT, wanted, null, 1, () -> false);
            } catch (IllegalArgumentException | DomainException e) {
                invalid = true;
                return new PerimeterOutcome(false, List.of());
            }
            qualified = EvalRuns.retrying(() -> analyze.qualify(analysis, Language.FR, List.of(), question));
            return new PerimeterOutcome(false, List.of());
        }

        @Override
        public int stepsLeft() {
            return 5 - steps;
        }

        @Override
        public boolean perimeterDone() {
            return terms != null;
        }

        @Override
        public boolean cancelled() {
            return false;
        }
    }

    private static CaseRun analyze(ConversationLlm llm, String sheet, Path tree, String name, String question,
            Map<String, String> expected, int run) {
        InMemorySourceRoots roots = new InMemorySourceRoots();
        roots.save(new SourceRoot(UUID.randomUUID(), PROJECT, tree.toString(), ExclusionPatterns.NONE, Instant.EPOCH));
        AnalyzePerimeter analyze = new AnalyzePerimeter(roots, new LocalSourceTree(), llm, new FixedClock());
        PerimeterTools tools = new PerimeterTools(analyze, question);
        EvalRuns.retrying(() -> {
            tools.reset();
            return llm.converse(new ConversationLlm.AgentRequest(Language.FR, sheet, "", List.of(), question, List.of(),
                    List.of(), 5, null, true, List.of()), tools, d -> { });
        });
        if (tools.terms == null) {
            return new CaseRun(name, run, List.of(), expected, Map.of(), "PAS_D_ANALYSE");
        }
        if (tools.invalid) {
            return new CaseRun(name, run, tools.terms, expected, Map.of(), "TERMES_INVALIDES");
        }
        Map<String, String> found = new LinkedHashMap<>();
        for (PerimeterModule m : tools.qualified.analysis().modules()) {
            found.put(m.module().name(), m.qualification().name());
        }
        return new CaseRun(name, run, tools.qualified.analysis().terms(), expected, found,
                tools.qualified.failed() ? "QUALIFICATION" : null);
    }

    /** Moyenne des exécutions de la moyenne des cas, pour le rappel ou l'accord. */
    static double score(List<CaseRun> all, String metric, int runs) {
        double sum = 0;
        for (int run = 1; run <= runs; run++) {
            int r = run;
            sum += all.stream().filter(c -> c.run() == r)
                    .mapToDouble(c -> EvalBaseline.PERIMETER_TERM_RECALL.equals(metric) ? c.recall() : c.agreement())
                    .average().orElse(1.0);
        }
        return sum / runs;
    }

    static Path write(Path dir, JsonNode files) {
        files.properties().forEach(f -> {
            Path file = dir.resolve(f.getKey());
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, f.getValue().asString());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return dir;
    }

    static Path temp() {
        try {
            return Files.createTempDirectory("sw-eval-perimetre-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void delete(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
