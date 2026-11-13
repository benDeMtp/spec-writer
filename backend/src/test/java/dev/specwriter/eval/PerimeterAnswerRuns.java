package dev.specwriter.eval;

import dev.specwriter.application.conversation.ConversationSearch;
import dev.specwriter.application.conversation.Converse;
import dev.specwriter.application.conversation.DesignatedDocuments;
import dev.specwriter.application.conversation.TurnEvents;
import dev.specwriter.application.sources.AnalyzePerimeter;
import dev.specwriter.application.specification.OperationRegistry;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentRecord;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.port.ConversationLlm;
import dev.specwriter.domain.port.ModelCapacities;
import dev.specwriter.domain.project.ExclusionPatterns;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import dev.specwriter.domain.sources.PerimeterAnalysis;
import dev.specwriter.domain.sources.PerimeterModule;
import dev.specwriter.domain.sources.Qualification;
import dev.specwriter.domain.sources.SourceRoot;
import dev.specwriter.infrastructure.sources.DemoTree;
import dev.specwriter.infrastructure.sources.LocalSourceTree;
import dev.specwriter.support.FakeEmbedding;
import dev.specwriter.support.FakeLocalSearch;
import dev.specwriter.support.FixedClock;
import dev.specwriter.support.InMemoryConsents;
import dev.specwriter.support.InMemoryConversations;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import dev.specwriter.support.InMemoryPerimeters;
import dev.specwriter.support.InMemoryProjects;
import dev.specwriter.support.InMemorySourceRoots;
import dev.specwriter.support.SequentialIdGenerator;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Réponse à une question de périmètre (013, T062) : la conversation entière tourne sur un arbre de
 * l'évaluation (plan, analyse, qualification, réponse). La réponse cite les modules que l'analyse
 * juge impactés, et ne cite aucun module qu'elle juge non impacté : la liste complète est affichée à
 * côté, la réponse n'a pas à la recopier.
 */
final class PerimeterAnswerRuns {

    /** Cas de {@code eval/perimeter/cases.json} rejoués comme question de conversation. */
    static final List<String> CASES = List.of("statut-suspendu", "paiement-paypal");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Résultat d'une réponse : modules impactés et non impactés de l'analyse, modules cités. */
    record CaseRun(String caseName, int run, List<String> impacted, List<String> notImpacted, List<String> cited,
            String error, String excerpt) {

        boolean passed() {
            return error == null && !impacted.isEmpty() && cited.containsAll(impacted)
                    && notImpacted.stream().noneMatch(cited::contains);
        }
    }

    private PerimeterAnswerRuns() {
    }

    static List<CaseRun> run(ConversationLlm llm, int run) {
        JsonNode root = JSON.readTree(EvalRuns.read(PerimeterRuns.CASES));
        Path work = PerimeterRuns.temp();
        try {
            Map<String, Path> trees = new LinkedHashMap<>();
            trees.put("demo-rv", DemoTree.copy(work));
            root.path("trees").properties()
                    .forEach(t -> trees.put(t.getKey(), PerimeterRuns.write(work.resolve(t.getKey()), t.getValue())));
            List<CaseRun> out = new ArrayList<>();
            for (JsonNode c : root.path("cases")) {
                String name = c.path("name").asString();
                if (CASES.contains(name)) {
                    out.add(ask(llm, root.path("sheet").asString(), trees.get(c.path("tree").asString()), name,
                            c.path("question").asString(), run));
                }
            }
            return out;
        } finally {
            PerimeterRuns.delete(work);
        }
    }

    private static CaseRun ask(ConversationLlm llm, String sheet, Path tree, String name, String question, int run) {
        InMemoryProjects projects = new InMemoryProjects();
        FixedClock clock = new FixedClock();
        projects.save(projects.find(Project.DEFAULT_ID).orElseThrow()
                .withContext(ProjectContext.edited(sheet, Instant.EPOCH)));
        InMemoryConsents consents = new InMemoryConsents();
        consents.record(new ConsentRecord(ConsentService.COPILOT, true, Instant.EPOCH));
        InMemoryConversations conversations = new InMemoryConversations();
        InMemorySourceRoots roots = new InMemorySourceRoots();
        roots.save(new SourceRoot(UUID.randomUUID(), Project.DEFAULT_ID, tree.toString(), ExclusionPatterns.NONE,
                Instant.EPOCH));
        InMemoryPerimeters perimeters = new InMemoryPerimeters();
        ConversationSearch nothing = new ConversationSearch(new InMemoryKnowledgeIndex(), new FakeEmbedding(),
                new FakeLocalSearch()) {
            @Override
            public List<Found> run(UUID projectId, String query, Set<dev.specwriter.domain.conversation.SourceKind> kinds) {
                return List.of();
            }
        };
        Converse converse = new Converse(projects, conversations, llm, nothing, DesignatedDocuments.NONE,
                ModelCapacities.UNKNOWN, new ConsentPolicy(consents), new OperationRegistry(), new TurnEvents(clock),
                clock, new SequentialIdGenerator(), Runnable::run, null,
                new AnalyzePerimeter(roots, new LocalSourceTree(), llm, clock), perimeters);
        Conversation conversation = converse.create(Project.DEFAULT_ID);
        Converse.Started started = converse.send(conversation.id(), question);
        ConversationMessage answer = conversations.messages(conversation.id()).stream()
                .filter(m -> m.id().equals(started.turnId())).findFirst().orElseThrow();
        List<String> cited = conversations.sources(answer.id()).stream().map(s -> s.ref()).toList();
        List<PerimeterAnalysis> analyses = perimeters.list(answer.id());
        String excerpt = answer.text().length() <= ConversationRuns.EXCERPT ? answer.text()
                : answer.text().substring(0, ConversationRuns.EXCERPT) + "…";
        if (analyses.isEmpty()) {
            return new CaseRun(name, run, List.of(), List.of(), cited, "PAS_D_ANALYSE", excerpt);
        }
        PerimeterAnalysis analysis = analyses.getLast();
        List<String> impacted = refs(analysis, Qualification.IMPACTED);
        List<String> notImpacted = refs(analysis, Qualification.NOT_IMPACTED);
        String error = answer.text().isBlank() ? "SANS_REPONSE" : impacted.isEmpty() ? "AUCUN_IMPACTE" : null;
        return new CaseRun(name, run, impacted, notImpacted, cited, error, excerpt);
    }

    private static List<String> refs(PerimeterAnalysis analysis, Qualification qualification) {
        return analysis.modules().stream().filter(m -> m.qualification() == qualification)
                .map(PerimeterModule::module).map(m -> "module:" + m.id()).toList();
    }

    /** Part des réponses réussies, moyenne des exécutions. */
    static double score(List<CaseRun> all, int runs) {
        double sum = 0;
        for (int run = 1; run <= runs; run++) {
            int r = run;
            List<CaseRun> of = all.stream().filter(c -> c.run() == r).toList();
            sum += of.isEmpty() ? 1.0 : (double) of.stream().filter(CaseRun::passed).count() / of.size();
        }
        return sum / runs;
    }
}
