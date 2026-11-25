package fr.kawamind.eval;

import fr.kawamind.application.conversation.ConversationSearch;
import fr.kawamind.application.conversation.Converse;
import fr.kawamind.application.conversation.DesignatedDocuments;
import fr.kawamind.application.conversation.DiscussSpecification;
import fr.kawamind.application.conversation.ProposeRevision;
import fr.kawamind.application.conversation.TurnEvents;
import fr.kawamind.application.knowledge.SessionKnowledge;
import fr.kawamind.application.specification.OperationRegistry;
import fr.kawamind.application.specification.ProposalStore;
import fr.kawamind.domain.consent.ConsentPolicy;
import fr.kawamind.domain.consent.ConsentRecord;
import fr.kawamind.domain.consent.ConsentService;
import fr.kawamind.domain.conversation.ConversationMessage;
import fr.kawamind.domain.conversation.MessageState;
import fr.kawamind.domain.conversation.SourceKind;
import fr.kawamind.domain.port.ConversationLlm;
import fr.kawamind.domain.port.ModelCapacities;
import fr.kawamind.domain.project.Project;
import fr.kawamind.domain.project.ProjectContext;
import fr.kawamind.domain.question.Questionnaire;
import fr.kawamind.domain.session.Language;
import fr.kawamind.domain.session.Need;
import fr.kawamind.domain.session.Session;
import fr.kawamind.domain.specification.MarkdownRenderer;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.SpecificationVersion;
import fr.kawamind.domain.specification.UseCase;
import fr.kawamind.domain.specification.VersionOrigin;
import fr.kawamind.support.FakeEmbedding;
import fr.kawamind.support.FakeLocalSearch;
import fr.kawamind.support.FixedClock;
import fr.kawamind.support.InMemoryConsents;
import fr.kawamind.support.InMemoryConversations;
import fr.kawamind.support.InMemoryKnowledgeIndex;
import fr.kawamind.support.InMemoryProjects;
import fr.kawamind.support.InMemorySessions;
import fr.kawamind.support.InMemorySpecificationDiscussions;
import fr.kawamind.support.InMemorySpecifications;
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
        QUESTION, INCONSISTENCY, REVISION, UNCHANGED, INTERVIEW
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
                case INTERVIEW -> w.interview(c, spec, run);
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
         * Révision d'après une séance (021, FR-015) : les hypothèses tranchées ne figurent plus dans la
         * proposition, chaque question reportée y est un point ouvert, et le changement noté y est.
         */
        CaseRun interview(JsonNode c, SpecificationContent spec, int run) {
            List<fr.kawamind.domain.specification.Assumption> assumptions = new ArrayList<>();
            for (JsonNode a : c.path("assumptions")) {
                assumptions.add(new fr.kawamind.domain.specification.Assumption("H-%02d".formatted(assumptions.size() + 1),
                        a.asString(), fr.kawamind.domain.specification.Assumption.Origin.TOOL_INFERENCE, null));
            }
            SpecificationContent read = spec.withAssumptions(assumptions);
            versions.save(new SpecificationVersion(ids.next(), session.id(), 2, read, "eval-2", VersionOrigin.DISCUSSION,
                    MarkdownRenderer.render(read, Language.FR), clock.now()));
            fr.kawamind.support.InMemoryInterviews interviews = new fr.kawamind.support.InMemoryInterviews();
            fr.kawamind.application.interview.RunInterview meet = new fr.kawamind.application.interview.RunInterview(
                    interviews, sessions, versions, operations, clock, ids);
            List<String> participants = new ArrayList<>();
            c.path("participants").forEach(n -> participants.add(n.asString()));
            UUID id = meet.startForSpecification(session.id(), participants).interview().id();
            List<String> decided = new ArrayList<>();
            List<String> deferred = new ArrayList<>();
            for (JsonNode p : c.path("points")) {
                JsonNode a = p.path("anchor");
                var anchor = a.isMissingNode() || a.isNull() ? null
                        : new fr.kawamind.domain.specification.Anchor(a.path("element").asString(),
                                a.path("step").isMissingNode() ? null : a.path("step").asInt(), false);
                var origin = fr.kawamind.domain.interview.InterviewPoint.Origin.valueOf(p.path("origin").asString());
                var outcome = fr.kawamind.domain.interview.InterviewPoint.Outcome.valueOf(p.path("outcome").asString());
                String code = p.path("code").isMissingNode() ? null : p.path("code").asString();
                meet.savePoint(id, null, new fr.kawamind.application.interview.RunInterview.PointInput(session.id(), anchor,
                        origin, code, p.path("text").asString(), outcome,
                        p.path("owner").isMissingNode() ? null : p.path("owner").asString(), null, null));
                if (code != null && outcome != fr.kawamind.domain.interview.InterviewPoint.Outcome.DEFERRED) {
                    assumptions.stream().filter(h -> h.code().equals(code)).forEach(h -> decided.add(h.statement()));
                }
                if (outcome == fr.kawamind.domain.interview.InterviewPoint.Outcome.DEFERRED) {
                    deferred.add(p.path("text").asString());
                }
            }
            meet.finish(id);
            ProposalStore proposals = new ProposalStore((x, y, l1, l2) -> "", ids, clock);
            ProposeRevision revise = new ProposeRevision(sessions, versions, conversations, discussions,
                    SessionKnowledge.sheetOnly(projects), llm, ModelCapacities.UNKNOWN, consent, operations, events,
                    proposals, clock, ids, Runnable::run, null, interviews);
            var revision = new fr.kawamind.application.interview.InterviewRevision(interviews, sessions, versions,
                    conversations, discussions, revise, consent, clock, ids);
            List<TurnEvents.Event> seen = new ArrayList<>();
            events.subscribe(revision.propose(id, session.id()).turnId(), seen::add);
            boolean removed = false;
            String outcome;
            String excerpt = "";
            if (seen.getLast() instanceof TurnEvents.Event.Done done && done.proposalId() != null) {
                ProposalStore.Proposal p = proposals.find(session.id(), done.proposalId()).orElseThrow();
                outcome = "PROPOSITION";
                excerpt = excerpt(p.markdown());
                removed = p.content().assumptions().stream().noneMatch(h -> decided.contains(h.statement()))
                        && deferred.stream().allMatch(q -> p.content().openQuestions().stream()
                                .anyMatch(o -> o.question().equals(q)));
            } else {
                outcome = seen.getLast() instanceof TurnEvents.Event.Error e ? e.code() : "AUCUN_CHANGEMENT";
            }
            return new CaseRun(c.path("name").asString(), Kind.INTERVIEW, run,
                    Map.of(EvalBaseline.DECIDED_REMOVED, removed), List.of(), outcome, excerpt);
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
