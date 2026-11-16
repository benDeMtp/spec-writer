package fr.kawamind.eval;

import fr.kawamind.application.conversation.ConversationSearch;
import fr.kawamind.application.conversation.Converse;
import fr.kawamind.application.conversation.TurnEvents;
import fr.kawamind.application.specification.OperationRegistry;
import fr.kawamind.domain.consent.ConsentPolicy;
import fr.kawamind.domain.consent.ConsentRecord;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.conversation.Conversation;
import fr.kawamind.domain.conversation.ConversationMessage;
import fr.kawamind.domain.conversation.SourceKind;
import fr.kawamind.domain.port.ConversationLlm;
import fr.kawamind.domain.port.ModelCapacities;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectContext;
import fr.kawamind.support.FakeEmbedding;
import fr.kawamind.support.FakeLocalSearch;
import fr.kawamind.support.FixedClock;
import fr.kawamind.support.InMemoryConsents;
import fr.kawamind.support.InMemoryConversations;
import fr.kawamind.support.InMemoryKnowledgeIndex;
import fr.kawamind.support.InMemoryProjects;
import fr.kawamind.support.SequentialIdGenerator;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Volet conversationnel de l'évaluation (010, T027) : sur le projet fictif Aurora de
 * {@code eval/conversation/cases.json}, l'agent répond à des questions à source attendue, à des
 * questions hors de sa connaissance et à des paires de sources contradictoires. Toute recherche de
 * l'agent renvoie le corpus entier : c'est sa lecture des sources qui est mesurée, pas l'index.
 */
final class ConversationRuns {

    static final Path CASES = Path.of("src/test/resources/eval/conversation/cases.json");
    /** Début de la réponse gardé dans last-run.json pour comprendre un échec (données fictives). */
    static final int EXCERPT = 400;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    enum Kind {
        CITATION, ABSTENTION, INCONSISTENCY
    }

    /** Résultat d'une question : sources citées et état de la réponse. */
    record CaseRun(String caseName, Kind kind, int run, List<String> expected, List<String> cited, String state,
            String error, String excerpt) {

        boolean passed() {
            if (error != null || !"COMPLETE".equals(state)) {
                return false;
            }
            return switch (kind) {
                case CITATION -> cited.containsAll(expected);
                case ABSTENTION -> cited.isEmpty();
                case INCONSISTENCY -> cited.containsAll(expected);
            };
        }
    }

    private ConversationRuns() {
    }

    static List<CaseRun> run(ConversationLlm llm, int run) {
        JsonNode root = JSON.readTree(EvalRuns.read(CASES));
        List<ConversationSearch.Found> corpus = new ArrayList<>();
        root.path("corpus").forEach(n -> corpus.add(new ConversationSearch.Found(SourceKind.valueOf(n.path("kind").asString()),
                n.path("ref").asString(), n.path("title").asString(), n.path("text").asString())));
        List<CaseRun> out = new ArrayList<>();
        for (JsonNode c : root.path("cases")) {
            List<String> expected = new ArrayList<>();
            c.path("expected").forEach(e -> expected.add(e.asString()));
            out.add(ask(llm, root.path("sheet").asString(), corpus, c.path("name").asString(),
                    Kind.valueOf(c.path("kind").asString()), c.path("question").asString(), expected, run));
        }
        return out;
    }

    private static CaseRun ask(ConversationLlm llm, String sheet, List<ConversationSearch.Found> corpus, String name,
            Kind kind, String question, List<String> expected, int run) {
        InMemoryProjects projects = new InMemoryProjects();
        FixedClock clock = new FixedClock();
        projects.save(projects.find(Project.DEFAULT_ID).orElseThrow()
                .withContext(ProjectContext.edited(sheet, Instant.EPOCH)));
        InMemoryConsents consents = new InMemoryConsents();
        consents.record(new ConsentRecord(ConsentService.COPILOT, true, Instant.EPOCH));
        InMemoryConversations conversations = new InMemoryConversations();
        ConversationSearch everything = new ConversationSearch(new InMemoryKnowledgeIndex(), new FakeEmbedding(),
                new FakeLocalSearch()) {
            @Override
            public List<Found> run(UUID projectId, String query, Set<SourceKind> kinds) {
                return corpus;
            }
        };
        Converse converse = new Converse(projects, conversations, llm, everything, ModelCapacities.UNKNOWN,
                new ConsentPolicy(consents), new OperationRegistry(), new TurnEvents(clock), clock,
                new SequentialIdGenerator(), Runnable::run);
        Conversation conversation = converse.create(Project.DEFAULT_ID);
        Converse.Started started = converse.send(conversation.id(), question);
        ConversationMessage answer = conversations.messages(conversation.id()).stream()
                .filter(m -> m.id().equals(started.turnId())).findFirst().orElseThrow();
        List<String> cited = conversations.sources(answer.id()).stream().map(s -> s.ref()).toList();
        String error = answer.text().isBlank() ? "SANS_REPONSE" : null;
        String excerpt = answer.text().length() <= EXCERPT ? answer.text() : answer.text().substring(0, EXCERPT) + "…";
        return new CaseRun(name, kind, run, expected, cited, answer.state().name(), error, excerpt);
    }

    /** Part des cas réussis d'une sorte, moyenne des exécutions. */
    static double score(List<CaseRun> all, Kind kind, int runs) {
        double sum = 0;
        for (int run = 1; run <= runs; run++) {
            int r = run;
            List<CaseRun> of = all.stream().filter(c -> c.run() == r && c.kind() == kind).toList();
            sum += of.isEmpty() ? 1.0 : (double) of.stream().filter(CaseRun::passed).count() / of.size();
        }
        return sum / runs;
    }
}
