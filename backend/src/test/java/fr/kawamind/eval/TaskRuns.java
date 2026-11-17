package fr.kawamind.eval;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.TaskLlm;
import fr.kawamind.domain.session.Language;
import fr.kawamind.domain.sources.Qualification;
import fr.kawamind.domain.task.BasisKind;
import fr.kawamind.domain.task.ProposedTask;
import fr.kawamind.domain.task.TaskProposal;
import fr.kawamind.domain.task.TaskProposalRequest;
import fr.kawamind.infrastructure.llm.CopilotSdkGateway;
import fr.kawamind.infrastructure.llm.EvalTools;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Volet tâches de l'évaluation (015, T076, SC-001, SC-008) : chaque spécification de
 * {@code eval/tasks/cases.json} passe par {@code propose-tasks} sur le vrai Copilot, garde-fou
 * compris. Un juge dit si le résultat de chaque tâche se constate sans lire le code.
 *
 * <ul>
 *   <li>{@code taskBounds} : nombre de tâches dans les bornes, et aucun titre dicté par un contenu
 *       piégé ({@code forbidden}) ;</li>
 *   <li>{@code taskBasisExact} : tâches dont la base cite un module ou un cas d'usage donné, tel quel ;</li>
 *   <li>{@code taskModuleCoverage} : modules impactés couverts par au moins une tâche (cas à modules) ;</li>
 *   <li>{@code taskVerifiable} : tâches jugées vérifiables.</li>
 * </ul>
 * Une proposition en échec compte zéro partout.
 */
final class TaskRuns {

    static final Path CASES = Path.of("src/test/resources/eval/tasks/cases.json");
    static final List<String> VERDICTS = List.of("VERIFIABLE", "NON_VERIFIABLE");
    private static final Map<String, Object> SCHEMA = EvalTools.strict(Map.of("type", "object", "properties",
            Map.of("verdicts", Map.of("type", "array", "items", Map.of("type", "object", "properties", Map.of(
                    "index", Map.of("type", "integer"),
                    "verdict", Map.of("type", "string", "enum", VERDICTS),
                    "reason", Map.of("type", "string")))))));
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Une spécification du jeu d'essai ; {@code forbidden} : titre que dicte un contenu piégé. */
    record Case(String name, TaskProposalRequest request, String forbidden) {
    }

    /** Résultat d'un cas : tâches rendues et verdict du juge pour chacune. */
    record CaseRun(String caseName, int run, List<ProposedTask> tasks, List<String> verdicts, boolean bounds,
            double basisExact, Double moduleCoverage, double verifiable, String error) {
    }

    private TaskRuns() {
    }

    static List<Case> cases() {
        JsonNode root = JSON.readTree(EvalRuns.read(CASES));
        List<Case> out = new ArrayList<>();
        for (JsonNode c : root.path("cases")) {
            List<TaskProposalRequest.UseCaseRef> useCases = new ArrayList<>();
            c.path("useCases").forEach(u -> useCases.add(new TaskProposalRequest.UseCaseRef(u.path("code").asString(),
                    u.path("title").asString(), u.path("goal").asString())));
            List<TaskProposalRequest.ModuleRef> modules = new ArrayList<>();
            c.path("modules").forEach(m -> modules.add(new TaskProposalRequest.ModuleRef(m.path("name").asString(),
                    Qualification.valueOf(m.path("qualification").asString()), m.path("justification").asString())));
            List<String> existing = new ArrayList<>();
            c.path("existing").forEach(e -> existing.add(e.asString()));
            TaskProposalRequest request = new TaskProposalRequest(Language.valueOf(c.path("language").asString("FR")),
                    c.path("title").asString(), c.path("summary").asString(), useCases, modules, existing,
                    TaskProposal.Bounds.forExisting(existing.size()));
            out.add(new Case(c.path("name").asString(), request, c.path("forbidden").asString(null)));
        }
        return out;
    }

    static List<CaseRun> run(TaskLlm llm, CopilotSdkGateway gateway, String model, int run) {
        List<CaseRun> out = new ArrayList<>();
        for (Case c : cases()) {
            List<ProposedTask> tasks;
            try {
                tasks = EvalRuns.retrying(() -> llm.proposeTasks(c.request())).tasks();
            } catch (DomainException e) {
                // une sortie toujours non conforme compte contre la qualité ; le reste arrête l'évaluation
                if (e.code() != ErrorCode.LLM_INVALID_OUTPUT && e.code() != ErrorCode.TASKS_UNAVAILABLE) {
                    throw e;
                }
                out.add(failed(c, run, e.code().name()));
                continue;
            }
            out.add(evaluate(c, run, tasks, judge(gateway, model, c.request(), tasks)));
        }
        return out;
    }

    static CaseRun failed(Case c, int run, String error) {
        return new CaseRun(c.name(), run, List.of(), List.of(), false, 0, c.request().modules().isEmpty() ? null : 0.0,
                0, error);
    }

    /** Métriques d'un cas, sur des tâches rendues et des verdicts du juge (un par tâche). */
    static CaseRun evaluate(Case c, int run, List<ProposedTask> tasks, List<String> verdicts) {
        TaskProposalRequest r = c.request();
        boolean pirated = c.forbidden() != null && tasks.stream()
                .anyMatch(t -> t.title().toLowerCase(Locale.ROOT).contains(c.forbidden().toLowerCase(Locale.ROOT)));
        boolean bounds = !tasks.isEmpty() && tasks.size() >= r.bounds().min() && tasks.size() <= r.bounds().max()
                && !pirated;
        boolean byModule = r.basing() == TaskProposal.Basing.MODULES;
        Set<String> refs = byModule
                ? r.modules().stream().map(TaskProposalRequest.ModuleRef::name).collect(Collectors.toSet())
                : r.useCases().stream().map(TaskProposalRequest.UseCaseRef::code).collect(Collectors.toSet());
        BasisKind kind = byModule ? BasisKind.MODULE : BasisKind.USE_CASE;
        long exact = tasks.stream().filter(t -> t.basis().kind() == kind && refs.contains(t.basis().ref())).count();
        Double coverage = null;
        if (byModule) {
            List<String> impacted = r.modules().stream().filter(m -> m.qualification() == Qualification.IMPACTED)
                    .map(TaskProposalRequest.ModuleRef::name).toList();
            Set<String> based = tasks.stream().filter(t -> t.basis().kind() == BasisKind.MODULE)
                    .map(t -> t.basis().ref()).collect(Collectors.toSet());
            coverage = impacted.isEmpty() ? 1.0 : (double) impacted.stream().filter(based::contains).count() / impacted.size();
        }
        long verifiable = verdicts.stream().filter("VERIFIABLE"::equals).count();
        return new CaseRun(c.name(), run, tasks, verdicts, bounds, tasks.isEmpty() ? 0 : (double) exact / tasks.size(),
                coverage, tasks.isEmpty() ? 0 : (double) verifiable / tasks.size(), null);
    }

    private static List<String> judge(CopilotSdkGateway gateway, String model, TaskProposalRequest r,
            List<ProposedTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        StringBuilder prompt = new StringBuilder("<specification>\n").append(r.title()).append('\n').append(r.summary())
                .append('\n');
        r.useCases().forEach(u -> prompt.append(u.code()).append(" : ").append(u.title()).append('\n'));
        prompt.append("</specification>\n<taches>\n");
        for (int i = 0; i < tasks.size(); i++) {
            prompt.append(i + 1).append(". ").append(tasks.get(i).title()).append("\n   Résultat attendu : ")
                    .append(tasks.get(i).outcome()).append('\n');
        }
        prompt.append("</taches>");
        String system = EvalRuns.read(Path.of("src/test/resources/eval/prompts/judge-task.txt"));
        for (int attempt = 1; attempt <= 2; attempt++) {
            Map<Integer, String> byIndex = new HashMap<>();
            JSON.readTree(EvalRuns.retrying(() -> gateway.complete(model, system, prompt.toString(), SCHEMA)))
                    .path("verdicts").forEach(v -> byIndex.put(v.path("index").asInt(), v.path("verdict").asString("")));
            if (IntStream.rangeClosed(1, tasks.size()).allMatch(i -> VERDICTS.contains(byIndex.get(i)))) {
                return IntStream.rangeClosed(1, tasks.size()).mapToObj(byIndex::get).toList();
            }
        }
        throw new IllegalStateException("Le juge n'a pas rendu un verdict pour chaque tâche");
    }

    /** Moyenne, sur les exécutions, de la métrique moyenne des cas qui la mesurent. */
    static double score(List<CaseRun> all, String metric, int runs) {
        double sum = 0;
        for (int run = 1; run <= runs; run++) {
            int r = run;
            List<Double> values = all.stream().filter(c -> c.run() == r).map(c -> value(c, metric))
                    .filter(java.util.Objects::nonNull).toList();
            sum += values.isEmpty() ? 1.0 : values.stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
        }
        return sum / runs;
    }

    private static Double value(CaseRun c, String metric) {
        return switch (metric) {
            case EvalBaseline.TASK_BOUNDS -> c.bounds() ? 1.0 : 0.0;
            case EvalBaseline.TASK_BASIS_EXACT -> c.basisExact();
            case EvalBaseline.TASK_MODULE_COVERAGE -> c.moduleCoverage();
            case EvalBaseline.TASK_VERIFIABLE -> c.verifiable();
            default -> throw new IllegalArgumentException(metric);
        };
    }
}
