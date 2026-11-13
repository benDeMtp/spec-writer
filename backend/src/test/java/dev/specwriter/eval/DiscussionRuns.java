package dev.specwriter.eval;

import dev.specwriter.application.conversation.ConversationSearch;
import dev.specwriter.application.conversation.Converse;
import dev.specwriter.application.conversation.DesignatedDocuments;
import dev.specwriter.application.conversation.DiscussSpecification;
import dev.specwriter.application.conversation.ProposeRevision;
import dev.specwriter.application.conversation.TurnEvents;
import dev.specwriter.application.knowledge.SessionKnowledge;
import dev.specwriter.application.specification.OperationRegistry;
import dev.specwriter.application.specification.ProposalStore;
import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentRecord;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.MessageState;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.port.ConversationLlm;
import dev.specwriter.domain.port.ModelCapacities;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.session.Language;
import dev.specwriter.domain.session.Need;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.specification.MarkdownRenderer;
import dev.specwriter.domain.specification.SpecificationContent;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.UseCase;
import dev.specwriter.domain.specification.VersionOrigin;
import dev.specwriter.support.FakeEmbedding;
import dev.specwriter.support.FakeLocalSearch;
import dev.specwriter.support.FixedClock;
import dev.specwriter.support.InMemoryConsents;
import dev.specwriter.support.InMemoryConversations;
import dev.specwriter.support.InMemoryKnowledgeIndex;
import dev.specwriter.support.InMemoryProjects;
import dev.specwriter.support.InMemorySessions;
import dev.specwriter.support.InMemorySpecificationDiscussions;
import dev.specwriter.support.InMemorySpecifications;
import dev.specwriter.support.SequentialIdGenerator;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Volet « discussion d'une spécification » de l'évaluation (011, research.md R10) : sur la
 * spécification fictive d'Aurora de {@code eval/discussion/cases.json}, l'agent répond à des
 * questions sur la spécification (citée par section), signale l'écart connu à un ADR, et propose
 * une nouvelle version d'après une discussion, ou dit qu'elle n'a établi aucun changement. Toute
 * recherche renvoie le corpus entier : c'est la lecture des sources qui est mesurée.
 */
final class DiscussionRuns {

    static final Path CASES = Path.of("src/test/resources/eval/discussion/cases.json");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    enum Kind {
        QUESTION, INCONSISTENCY, REVISION, UNCHANGED
    }

    /** Contrôles d'un cas, par métrique ; {@code outcome} : réponse, proposition, aucun changement ou erreur. */
    record CaseRun(String caseName, Kind kind, int run, Map<String, Boolean> checks, List<String> cited, String outcome,
            String excerpt) {

        boolean passed() {
            return checks.values().stream().allMatch(Boolean::booleanValue);
        }
    }

    private DiscussionRuns() {
    }

    static List<CaseRun> run(ConversationLlm llm, int run) {
        JsonNode root = JSON.readTree(EvalRuns.read(CASES));
        List<ConversationSearch.Found> corpus = new ArrayList<>();
        root.path("corpus").forEach(n -> corpus.add(new ConversationSearch.Found(
                SourceKind.valueOf(n.path("kind").asString()), n.path("ref").asString(), n.path("title").asString(),
                n.path("text").asString())));
        SpecificationContent spec = JSON.treeToValue(root.path("specification"), SpecificationContent.class);
        List<CaseRun> out = new ArrayList<>();
        for (JsonNode c : root.path("cases")) {
            World w = new World(root, spec, corpus, llm);
            Kind kind = Kind.valueOf(c.path("kind").asString());
            out.add(switch (kind) {
                case QUESTION, INCONSISTENCY -> w.ask(c, kind, run);
                case REVISION, UNCHANGED -> w.propose(c, kind, spec, run);
            });
        }
        return out;
    }

    /** Une spécification discutée, dans un monde neuf pour chaque cas. */
    private static final class World {
        final FixedClock clock = new FixedClock();
        final SequentialIdGenerator ids = new SequentialIdGenerator();
        final InMemorySessions sessions = new InMemorySessions();
        final InMemorySpecifications versions = new InMemorySpecifications();
        final InMemoryConversations conversations = new InMemoryConversations();
        final InMemoryProjects projects = new InMemoryProjects();
        final ConsentPolicy consent;
        final OperationRegistry operations = new OperationRegistry();
        final TurnEvents events = new TurnEvents(clock);
        final DiscussSpecification discussions;
        final ConversationLlm llm;
        final List<ConversationSearch.Found> corpus;
        final Session session;
        final UUID conversationId;

        World(JsonNode root, SpecificationContent spec, List<ConversationSearch.Found> corpus, ConversationLlm llm) {
            this.llm = llm;
            this.corpus = corpus;
            projects.save(projects.find(Project.DEFAULT_ID).orElseThrow()
                    .withContext(ProjectContext.edited(root.path("sheet").asString(), Instant.EPOCH)));
            InMemoryConsents consents = new InMemoryConsents();
            consents.record(new ConsentRecord(ConsentService.COPILOT, true, Instant.EPOCH));
            consent = new ConsentPolicy(consents);
            session = Session.start(ids.next(), Project.DEFAULT_ID, Need.of(root.path("need").asString()),
                    root.path("name").asString(), clock.now()).generated(clock.now());
            sessions.save(session, Questionnaire.empty());
            versions.save(new SpecificationVersion(ids.next(), session.id(), 1, spec, "eval", VersionOrigin.GENERATED,
                    MarkdownRenderer.render(spec, Language.FR), clock.now()));
            discussions = new DiscussSpecification(sessions, versions, conversations,
                    new InMemorySpecificationDiscussions(), clock, ids);
            conversationId = discussions.open(session.id()).conversationId();
        }

        CaseRun ask(JsonNode c, Kind kind, int run) {
            ConversationSearch everything = new ConversationSearch(new InMemoryKnowledgeIndex(), new FakeEmbedding(),
                    new FakeLocalSearch()) {
                @Override
                public List<Found> run(UUID projectId, String query, Set<SourceKind> kinds) {
                    return corpus;
                }
            };
            Converse converse = new Converse(projects, conversations, llm, everything, DesignatedDocuments.NONE,
                    ModelCapacities.UNKNOWN, consent, operations, events, clock, ids, Runnable::run, discussions);
            Converse.Started started = converse.send(conversationId, c.path("question").asString());
            ConversationMessage answer = conversations.messages(conversationId).stream()
                    .filter(m -> m.id().equals(started.turnId())).findFirst().orElseThrow();
            List<String> cited = conversations.sources(answer.id()).stream().map(s -> s.ref()).toList();
            boolean found = answer.state() == MessageState.COMPLETE && !answer.text().isBlank();
            for (JsonNode e : c.path("expected")) {
                String expected = e.asString();
                found &= cited.stream().anyMatch(r -> expected.startsWith("#") ? r.endsWith(expected) : r.equals(expected));
            }
            String metric = kind == Kind.QUESTION ? EvalBaseline.DISCUSSION_CITATION
                    : EvalBaseline.DISCUSSION_INCONSISTENCY;
            return new CaseRun(c.path("name").asString(), kind, run, Map.of(metric, found), cited,
                    answer.state().name(), excerpt(answer.text()));
        }

        CaseRun propose(JsonNode c, Kind kind, SpecificationContent spec, int run) {
            int ordinal = 0;
            for (JsonNode line : c.path("discussion")) {
                ordinal++;
                String text = line.get(1).asString();
                conversations.saveMessage("USER".equals(line.get(0).asString())
                        ? ConversationMessage.user(ids.next(), conversationId, ordinal, text, clock.now())
                        : ConversationMessage.agentStarted(ids.next(), conversationId, ordinal, clock.now())
                                .withText(text).finished(MessageState.COMPLETE, false).about(1));
            }
            ProposalStore proposals = new ProposalStore((a, b, l1, l2) -> "", ids, clock);
            ProposeRevision revise = new ProposeRevision(sessions, versions, conversations, discussions,
                    SessionKnowledge.sheetOnly(projects), llm, ModelCapacities.UNKNOWN, consent, operations, events,
                    proposals, clock, ids, Runnable::run);
            Map<String, Boolean> checks = new LinkedHashMap<>();
            checks.putAll(changes(c, kind, revise));
            List<TurnEvents.Event> seen = new ArrayList<>();
            events.subscribe(revise.propose(conversationId).turnId(), seen::add);
            TurnEvents.Event last = seen.getLast();
            String outcome;
            String excerpt = "";
            if (last instanceof TurnEvents.Event.Done done && done.proposalId() != null) {
                ProposalStore.Proposal p = proposals.find(session.id(), done.proposalId()).orElseThrow();
                outcome = "PROPOSITION";
                excerpt = excerpt(p.markdown());
                if (kind == Kind.REVISION) {
                    String markdown = p.markdown().toLowerCase(Locale.ROOT);
                    boolean complete = true;
                    for (JsonNode m : c.path("mustContain")) {
                        complete &= markdown.contains(m.asString().toLowerCase(Locale.ROOT));
                    }
                    boolean preserved = true;
                    for (JsonNode t : c.path("preserve")) {
                        preserved &= kept(spec, p.content(), t.asString());
                    }
                    checks.put(EvalBaseline.REVISION_COMPLETENESS, complete);
                    checks.put(EvalBaseline.REVISION_PRESERVATION, preserved);
                    checks.put(EvalBaseline.REVISION_STRUCTURE, true);
                } else {
                    checks.put(EvalBaseline.UNCHANGED_RECALL, false);
                }
            } else if (last instanceof TurnEvents.Event.Done done && done.unchanged()) {
                outcome = "AUCUN_CHANGEMENT";
                excerpt = excerpt(done.reason());
                if (kind == Kind.REVISION) {
                    checks.put(EvalBaseline.REVISION_COMPLETENESS, false);
                    checks.put(EvalBaseline.REVISION_PRESERVATION, false);
                    checks.put(EvalBaseline.REVISION_STRUCTURE, true);
                } else {
                    checks.put(EvalBaseline.UNCHANGED_RECALL, true);
                }
            } else {
                outcome = last instanceof TurnEvents.Event.Error e ? e.code() : "INCONNU";
                if (kind == Kind.REVISION) {
                    checks.put(EvalBaseline.REVISION_COMPLETENESS, false);
                    checks.put(EvalBaseline.REVISION_PRESERVATION, false);
                    checks.put(EvalBaseline.REVISION_STRUCTURE, false);
                } else {
                    checks.put(EvalBaseline.UNCHANGED_RECALL, false);
                }
            }
            return new CaseRun(c.path("name").asString(), kind, run, checks, List.of(), outcome, excerpt);
        }

        /**
         * Relevé des changements avant la rédaction : une révision voit chaque changement attendu
         * relevé comme dit par la personne ; une discussion sans changement n'en voit aucun à son nom.
         */
        Map<String, Boolean> changes(JsonNode c, Kind kind, ProposeRevision revise) {
            List<ConversationLlm.ChangeCandidate> found;
            try {
                found = revise.changes(conversationId);
            } catch (RuntimeException e) {
                return Map.of(kind == Kind.REVISION ? EvalBaseline.CHANGES_RECALL : EvalBaseline.CHANGES_PRECISION,
                        false);
            }
            List<String> said = found.stream().filter(f -> f.origin() == ConversationLlm.ChangeOrigin.USER)
                    .map(f -> f.text().toLowerCase(Locale.ROOT)).toList();
            if (kind != Kind.REVISION) {
                return Map.of(EvalBaseline.CHANGES_PRECISION, said.isEmpty());
            }
            boolean all = !said.isEmpty();
            for (JsonNode m : c.path("mustContain")) {
                String term = m.asString().toLowerCase(Locale.ROOT);
                all &= said.stream().anyMatch(t -> t.contains(term));
            }
            return Map.of(EvalBaseline.CHANGES_RECALL, all);
        }
    }

    /** Le use case de ce titre est recopié : même acteur, même objectif, même déroulement. */
    static boolean kept(SpecificationContent before, SpecificationContent after, String title) {
        UseCase original = before.useCases().stream().filter(u -> u.title().equals(title)).findFirst().orElseThrow();
        return after.useCases().stream().anyMatch(u -> u.title().strip().equalsIgnoreCase(title)
                && u.actor().equals(original.actor()) && u.goal().equals(original.goal())
                && u.nominalFlow().equals(original.nominalFlow()));
    }

    private static String excerpt(String text) {
        String t = text == null ? "" : text;
        return t.length() <= ConversationRuns.EXCERPT ? t : t.substring(0, ConversationRuns.EXCERPT) + "…";
    }

    /** Part des cas réussis pour une métrique, moyenne des exécutions ; 1 sans cas. */
    static double score(List<CaseRun> all, String metric, int runs) {
        double sum = 0;
        for (int run = 1; run <= runs; run++) {
            int r = run;
            List<CaseRun> of = all.stream().filter(c -> c.run() == r && c.checks().containsKey(metric)).toList();
            sum += of.isEmpty() ? 1.0 : (double) of.stream().filter(c -> c.checks().get(metric)).count() / of.size();
        }
        return sum / runs;
    }
}
