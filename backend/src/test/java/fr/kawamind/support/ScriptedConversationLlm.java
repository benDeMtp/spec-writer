package fr.kawamind.support;

import fr.kawamind.domain.conversation.NeedDraft;
import fr.kawamind.domain.conversation.SourceKind;
import fr.kawamind.domain.conversation.TurnSource;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.port.ConversationLlm;
import fr.kawamind.domain.port.TurnTools;
import fr.kawamind.domain.session.Language;
import fr.kawamind.domain.sources.ModuleQualification;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Agent scripté (014) : il rejoue des étapes (recherche, analyse de périmètre) par les outils du
 * tour, puis une réponse en morceaux. Il garde, comme l'ancienne boucle plan/réponse, ce que l'agent
 * savait avant chaque choix ({@link PlanView}) et au moment de répondre ({@link AnswerView}).
 */
public class ScriptedConversationLlm implements ConversationLlm {

    /** Étape scriptée : chercher, analyser le périmètre, ou répondre. */
    public enum Action {
        SEARCH, PERIMETER, ANSWER
    }

    public record Plan(Action action, String query, Set<SourceKind> kinds, List<String> terms) {

        public Plan(Action action, String query, Set<SourceKind> kinds) {
            this(action, query, kinds, List.of());
        }

        public static Plan answer() {
            return new Plan(Action.ANSWER, null, Set.of());
        }

        public static Plan perimeter(List<String> terms) {
            return new Plan(Action.PERIMETER, null, Set.of(), terms);
        }
    }

    /** Ce que l'agent savait avant de choisir une étape. */
    public record PlanView(Language language, String generalSheet, String memory, List<Exchange> history,
            String question, List<TurnSource> found, int searchesLeft, Discussed discussed, boolean perimeterAvailable,
            List<String> lastPerimeterTerms) {
    }

    /** Ce que l'agent savait au moment de répondre. */
    public record AnswerView(Language language, String generalSheet, String memory, List<Exchange> history,
            String question, List<TurnSource> sources, List<String> notCompared, boolean searchStopped,
            Discussed discussed, boolean perimeterUnavailable) {
    }

    public final Deque<Plan> plans = new ArrayDeque<>();
    public final Deque<List<String>> answers = new ArrayDeque<>();
    public final List<PlanView> planRequests = new ArrayList<>();
    public final List<AnswerView> answerRequests = new ArrayList<>();
    public final List<AgentRequest> agentRequests = new ArrayList<>();
    public final Deque<NeedDraft> drafts = new ArrayDeque<>();
    public final List<DraftRequest> draftRequests = new ArrayList<>();
    /** Panne à la prochaine réponse, après l'émission de {@code failAfter} morceaux. */
    public DomainException failure;
    public int failAfter;
    public final List<CompactRequest> compactRequests = new ArrayList<>();
    public final Deque<Revision> revisions = new ArrayDeque<>();
    public final List<ReviseRequest> reviseRequests = new ArrayList<>();
    /** Relevés des changements successifs ; le dernier se répète. Vide : aucun changement. */
    public final Deque<List<ChangeCandidate>> changes = new ArrayDeque<>();
    public final List<ChangesRequest> changesRequests = new ArrayList<>();
    public String reviseReasoning;
    /** Panne à la prochaine proposition. */
    public DomainException reviseFailure;
    /** Panne au prochain compactage. */
    public DomainException compactFailure;

    /** Appelé après chaque morceau émis. */
    public Runnable afterChunk = () -> { };
    /** Réflexion émise avant chaque choix d'étape, avant la réponse, ou au compactage. */
    public String planReasoning;
    public String answerReasoning;
    public String compactReasoning;
    private Consumer<String> reasoning;

    @Override
    public void thinking(Consumer<String> sink, Runnable work) {
        reasoning = sink;
        try {
            work.run();
        } finally {
            reasoning = null;
        }
    }

    private void reason(String text) {
        if (reasoning != null && text != null) {
            reasoning.accept(text);
        }
    }

    /**
     * Un tour : les étapes scriptées passent par les outils du tour, jusqu'à une réponse, l'arrêt, ou
     * le plafond (une étape refusée vaut la fin de la recherche, comme le garde-fou de l'adaptateur).
     */
    @Override
    public String converse(AgentRequest request, TurnTools tools, Consumer<String> deltas) {
        agentRequests.add(request);
        List<TurnSource> found = new ArrayList<>(request.sources());
        List<String> notCompared = new ArrayList<>(request.notCompared());
        boolean perimeterUnavailable = false;
        while (!tools.cancelled() && tools.stepsLeft() > 0) {
            planRequests.add(new PlanView(request.language(), request.generalSheet(), request.memory(),
                    request.history(), request.question(), List.copyOf(found), tools.stepsLeft(), request.discussed(),
                    request.perimeterAvailable() && !tools.perimeterDone(), request.lastPerimeterTerms()));
            Plan plan = plans.isEmpty() ? Plan.answer() : plans.removeFirst();
            if (plan.action() != Action.ANSWER) {
                // réflexion d'un appel qui finit par un outil ; celle de la réponse vient après
                reason(planReasoning);
            }
            if (plan.action() == Action.ANSWER || plan.action() == Action.PERIMETER && tools.perimeterDone()) {
                break;
            }
            if (plan.action() == Action.PERIMETER) {
                TurnTools.PerimeterOutcome outcome = tools.perimeter(plan.terms());
                perimeterUnavailable |= outcome.unavailable();
                add(found, outcome.modules());
                continue;
            }
            if (plan.query() == null || plan.query().isBlank()) {
                break;
            }
            TurnTools.Found result = tools.search(plan.query(), plan.kinds());
            add(found, result.sources());
            notCompared.addAll(result.notCompared());
        }
        if (tools.cancelled()) {
            return "";
        }
        answerRequests.add(new AnswerView(request.language(), request.generalSheet(), request.memory(),
                request.history(), request.question(), List.copyOf(found), List.copyOf(notCompared),
                tools.stepsLeft() <= 0, request.discussed(), perimeterUnavailable));
        reason(answerReasoning);
        List<String> chunks = answers.isEmpty() ? List.of("Je ne trouve pas cette information dans le projet.")
                : answers.removeFirst();
        StringBuilder text = new StringBuilder();
        int emitted = 0;
        for (String chunk : chunks) {
            if (tools.cancelled()) {
                break;
            }
            if (failure != null && emitted == failAfter) {
                DomainException e = failure;
                failure = null;
                throw e;
            }
            text.append(chunk);
            deltas.accept(chunk);
            emitted++;
            afterChunk.run();
        }
        return text.toString();
    }

    /** Sources dans l'ordre de leur numéro, chacune une fois. */
    private static void add(List<TurnSource> found, List<TurnSource> more) {
        for (TurnSource s : more) {
            if (found.stream().noneMatch(f -> f.n() == s.n())) {
                found.add(s);
            }
        }
        found.sort(java.util.Comparator.comparingInt(TurnSource::n));
    }

    /** Qualifications scriptées (013) ; vide : aucun module qualifié. */
    public final Deque<List<ModuleQualification>> qualifications = new ArrayDeque<>();
    public final List<QualifyRequest> qualifyRequests = new ArrayList<>();
    /** Panne à la prochaine qualification. */
    public DomainException qualifyFailure;

    @Override
    public List<ModuleQualification> qualify(QualifyRequest request) {
        qualifyRequests.add(request);
        if (qualifyFailure != null) {
            DomainException e = qualifyFailure;
            qualifyFailure = null;
            throw e;
        }
        return qualifications.isEmpty() ? List.of() : qualifications.removeFirst();
    }

    @Override
    public NeedDraft draftNeed(DraftRequest request) {
        draftRequests.add(request);
        return drafts.removeFirst();
    }

    @Override
    public Revision revise(ReviseRequest request) {
        reviseRequests.add(request);
        reason(reviseReasoning);
        if (reviseFailure != null) {
            DomainException e = reviseFailure;
            reviseFailure = null;
            throw e;
        }
        return revisions.size() > 1 ? revisions.removeFirst() : revisions.getFirst();
    }

    @Override
    public List<ChangeCandidate> changes(ChangesRequest request) {
        changesRequests.add(request);
        if (changes.isEmpty()) {
            return List.of();
        }
        return changes.size() > 1 ? changes.removeFirst() : changes.getFirst();
    }

    /** Résumé scripté : « Résumé n » ; n compte les compactages. */
    @Override
    public String compact(CompactRequest request) {
        compactRequests.add(request);
        reason(compactReasoning);
        if (compactFailure != null) {
            DomainException e = compactFailure;
            compactFailure = null;
            throw e;
        }
        return "Résumé " + compactRequests.size();
    }
}
