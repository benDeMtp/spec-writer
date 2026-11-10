package dev.specwriter.support;

import dev.specwriter.domain.conversation.NeedDraft;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.port.ConversationLlm;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Agent scripté : plans et réponses (en morceaux) dans l'ordre ; garde les requêtes reçues. */
public class ScriptedConversationLlm implements ConversationLlm {

    public final Deque<Plan> plans = new ArrayDeque<>();
    public final Deque<List<String>> answers = new ArrayDeque<>();
    public final List<PlanRequest> planRequests = new ArrayList<>();
    public final List<AnswerRequest> answerRequests = new ArrayList<>();
    public final Deque<NeedDraft> drafts = new ArrayDeque<>();
    public final List<DraftRequest> draftRequests = new ArrayList<>();
    /** Panne à la prochaine réponse, après l'émission de {@code failAfter} morceaux. */
    public DomainException failure;
    public int failAfter;
    public final List<CompactRequest> compactRequests = new ArrayList<>();
    public final Deque<Revision> revisions = new ArrayDeque<>();
    public final List<ReviseRequest> reviseRequests = new ArrayList<>();
    public String reviseReasoning;
    /** Panne à la prochaine proposition. */
    public DomainException reviseFailure;
    /** Panne au prochain compactage. */
    public DomainException compactFailure;

    /** Appelé après chaque morceau émis. */
    public Runnable afterChunk = () -> { };
    /** Réflexion émise au début de chaque appel de ce type, quand l'écran la montre. */
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

    @Override
    public Plan plan(PlanRequest request) {
        planRequests.add(request);
        reason(planReasoning);
        return plans.isEmpty() ? Plan.answer() : plans.removeFirst();
    }

    @Override
    public String answer(AnswerRequest request, Consumer<String> deltas, BooleanSupplier cancelled) {
        answerRequests.add(request);
        reason(answerReasoning);
        List<String> chunks = answers.isEmpty() ? List.of("Je ne trouve pas cette information dans le projet.")
                : answers.removeFirst();
        StringBuilder text = new StringBuilder();
        int emitted = 0;
        for (String chunk : chunks) {
            if (cancelled.getAsBoolean()) {
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
