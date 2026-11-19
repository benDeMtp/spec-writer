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
import fr.kawamind.domain.knowledge.Provenance;
import fr.kawamind.domain.knowledge.Reliability;
import fr.kawamind.domain.knowledge.ReservedPlaces;
import fr.kawamind.domain.knowledge.SourceOrigin;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Volet « fiabilité des sources » de l'évaluation (016, R9) : sur le projet fictif Hermès de
 * {@code eval/sources/cases.json}, chaque recherche de l'agent rend un classement figé où les
 * extraits de référence sont mal classés ; les places réservées les font entrer dans les résultats
 * ({@code referenceRetained}, déterministe), puis l'agent doit suivre la référence et signaler
 * l'écart en citant les deux extraits, sans en inventer quand les sources s'accordent
 * ({@code sourceConflictFlagged}).
 */
final class SourceRuns {

    static final Path CASES = Path.of("src/test/resources/eval/sources/cases.json");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Mots d'un écart signalé : absents d'une réponse sur des sources qui s'accordent. */
    private static final Pattern DEVIATION = Pattern.compile("écart|contradi|incohéren|diverge|diffère|discordan");
    /** Écart nié (« pas d'écart », « ne s'écarte pas ») : la réponse dit justement que les sources s'accordent. */
    private static final Pattern DENIED = Pattern.compile("(pas|aucun|aucune|sans|ni)\\s+(d['’]|de\\s+|la\\s+moindre\\s+)?"
            + "(écart|contradiction|incohérence|divergence)s?|ne\\s+(se\\s+)?(s['’])?(écarte|contredi\\w*|diverge|diffère)\\w*\\s+pas"
            + "|sans\\s+(se\\s+)?contredire");

    enum Kind {
        /** Une interprétation plus récente contredit la référence. */
        CONFLICT,
        /** Référence et interprétation disent la même chose à des dates différentes. */
        AGREEMENT,
        /** Deux références se contredisent : aucune ne l'emporte, l'écart est signalé. */
        TWO_REFERENCES
    }

    /** Résultat d'une question. */
    record CaseRun(String caseName, Kind kind, int run, List<String> cite, String value, List<String> forbidden,
            List<String> cited, String text, String state, boolean referenceRetained, String error) {

        boolean passed() {
            if (error != null || !"COMPLETE".equals(state) || !cited.containsAll(cite)) {
                return false;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            if (forbidden.stream().anyMatch(f -> normalized(text).contains(normalized(f)))) {
                return false;
            }
            return switch (kind) {
                case CONFLICT -> normalized(text).contains(value);
                case AGREEMENT -> normalized(text).contains(value)
                        && !DEVIATION.matcher(DENIED.matcher(lower).replaceAll("")).find();
                case TWO_REFERENCES -> true;
            };
        }

        /** Espaces insécables et fines retirés : « 1 000 », « 1 000 » et « 1000 » se valent. */
        private static String normalized(String s) {
            return s.replaceAll("[\\s\\u00a0\\u202f]", "");
        }
    }

    private SourceRuns() {
    }

    /** Un extrait du corpus et sa provenance. */
    static Map<String, ConversationSearch.Found> corpus(JsonNode root) {
        Map<String, ConversationSearch.Found> out = new LinkedHashMap<>();
        root.path("corpus").forEach(n -> out.put(n.path("id").asString(), new ConversationSearch.Found(
                SourceKind.valueOf(n.path("kind").asString()), n.path("ref").asString(), n.path("title").asString(),
                n.path("text").asString(), new Provenance(SourceOrigin.valueOf(n.path("origin").asString()),
                        Instant.parse(n.path("updatedAt").asString()), Reliability.valueOf(n.path("level").asString())))));
        return out;
    }

    /** Résultats d'une recherche : le classement figé du cas, après les places réservées. */
    static List<ConversationSearch.Found> results(Map<String, ConversationSearch.Found> corpus, List<String> ranked) {
        return ReservedPlaces.apply(ranked.stream().map(corpus::get).toList(), ConversationSearch.MAX_RESULTS,
                ConversationSearch.Found::reference);
    }

    static List<CaseRun> run(ConversationLlm llm, int run) {
        JsonNode root = JSON.readTree(EvalRuns.read(CASES));
        Map<String, ConversationSearch.Found> corpus = corpus(root);
        List<CaseRun> out = new ArrayList<>();
        for (JsonNode c : root.path("cases")) {
            List<String> ranked = strings(c.path("ranked"));
            List<ConversationSearch.Found> found = results(corpus, ranked);
            List<String> references = strings(c.path("reference")).stream().map(id -> corpus.get(id).ref()).toList();
            boolean retained = found.stream().map(ConversationSearch.Found::ref).toList().containsAll(references);
            List<String> cite = strings(c.path("cite")).stream().map(id -> corpus.get(id).ref()).toList();
            out.add(ask(llm, root.path("sheet").asString(), found, c, cite, retained, run));
        }
        return out;
    }

    private static CaseRun ask(ConversationLlm llm, String sheet, List<ConversationSearch.Found> found, JsonNode c,
            List<String> cite, boolean retained, int run) {
        InMemoryProjects projects = new InMemoryProjects();
        FixedClock clock = new FixedClock();
        projects.save(projects.find(Project.DEFAULT_ID).orElseThrow()
                .withContext(ProjectContext.edited(sheet, Instant.EPOCH)));
        InMemoryConsents consents = new InMemoryConsents();
        consents.record(new ConsentRecord(ConsentService.COPILOT, true, Instant.EPOCH));
        InMemoryConversations conversations = new InMemoryConversations();
        ConversationSearch fixed = new ConversationSearch(new InMemoryKnowledgeIndex(), new FakeEmbedding(),
                new FakeLocalSearch()) {
            @Override
            public List<Found> run(UUID projectId, String query, Set<SourceKind> kinds) {
                return found;
            }
        };
        Converse converse = new Converse(projects, conversations, llm, fixed, ModelCapacities.UNKNOWN,
                new ConsentPolicy(consents), new OperationRegistry(), new TurnEvents(clock), clock,
                new SequentialIdGenerator(), Runnable::run);
        Conversation conversation = converse.create(Project.DEFAULT_ID);
        Converse.Started started = converse.send(conversation.id(), c.path("question").asString());
        ConversationMessage answer = conversations.messages(conversation.id()).stream()
                .filter(m -> m.id().equals(started.turnId())).findFirst().orElseThrow();
        List<String> cited = conversations.sources(answer.id()).stream().map(s -> s.ref()).toList();
        return new CaseRun(c.path("name").asString(), Kind.valueOf(c.path("kind").asString()), run, cite,
                c.path("value").asString(), strings(c.path("forbidden")), cited, answer.text(), answer.state().name(),
                retained, answer.text().isBlank() ? "SANS_REPONSE" : null);
    }

    private static List<String> strings(JsonNode node) {
        List<String> out = new ArrayList<>();
        node.forEach(n -> out.add(n.asString()));
        return out;
    }

    /** {@code sourceConflictFlagged} : part des questions réussies, moyenne des exécutions. */
    static double conflictScore(List<CaseRun> all, int runs) {
        return average(all, runs, CaseRun::passed);
    }

    /** {@code referenceRetained} : part des recherches dont les références pertinentes sont retenues. */
    static double retainedScore(List<CaseRun> all, int runs) {
        return average(all, runs, CaseRun::referenceRetained);
    }

    private static double average(List<CaseRun> all, int runs, java.util.function.Predicate<CaseRun> ok) {
        double sum = 0;
        for (int run = 1; run <= runs; run++) {
            int r = run;
            List<CaseRun> of = all.stream().filter(c -> c.run() == r).toList();
            sum += of.isEmpty() ? 1.0 : (double) of.stream().filter(ok).count() / of.size();
        }
        return sum / runs;
    }
}
