package dev.specwriter.support;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.BuildContextRequest;
import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.llm.EditRequest;
import dev.specwriter.domain.llm.GenerationRequest;
import dev.specwriter.domain.llm.MergeRequest;
import dev.specwriter.domain.llm.ProjectContextDraft;
import dev.specwriter.domain.llm.ProposeQuestionsRequest;
import dev.specwriter.domain.llm.ProposeQuestionsResult;
import dev.specwriter.domain.llm.ReassessRequest;
import dev.specwriter.domain.llm.ReassessResult;
import dev.specwriter.domain.llm.SourceFacts;
import dev.specwriter.domain.llm.StructureImportRequest;
import dev.specwriter.domain.llm.SummarizeSourcesRequest;
import dev.specwriter.domain.port.LlmClient;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/** Port LLM simulé : réponses en file ; une file vide signifie « indisponible ». */
public class StubLlm implements LlmClient {
    public final Deque<ProposeQuestionsResult> questions = new ArrayDeque<>();
    public final Deque<DraftSpecification> drafts = new ArrayDeque<>();
    public final Deque<ReassessResult> reassessments = new ArrayDeque<>();
    public final List<ReassessRequest> reassessRequests = new ArrayList<>();
    public final List<ProposeQuestionsRequest> questionRequests = new ArrayList<>();
    public final List<GenerationRequest> generationRequests = new ArrayList<>();
    public final Deque<DraftSpecification> edits = new ArrayDeque<>();
    public final Deque<DraftSpecification> merges = new ArrayDeque<>();
    public final Deque<DraftSpecification> structures = new ArrayDeque<>();
    public final List<EditRequest> editRequests = new ArrayList<>();
    public final List<MergeRequest> mergeRequests = new ArrayList<>();
    public final List<StructureImportRequest> structureRequests = new ArrayList<>();
    public final Deque<SourceFacts> facts = new ArrayDeque<>();
    public final Deque<ProjectContextDraft> contexts = new ArrayDeque<>();
    public final List<SummarizeSourcesRequest> summarizeRequests = new ArrayList<>();
    public final List<BuildContextRequest> contextRequests = new ArrayList<>();
    /** Erreur levée par {@code structureImportedText} à la place d'une réponse. */
    public ErrorCode structureFailure;
    /** Appelé avant chaque rédaction du contexte : simule un appel long, par exemple. */
    public volatile Runnable onBuildContext;

    public static ProposeQuestionsResult none() {
        return new ProposeQuestionsResult(List.of(), List.of(), Set.of());
    }

    @Override
    public ProposeQuestionsResult proposeQuestions(ProposeQuestionsRequest request) {
        questionRequests.add(request);
        if (questions.isEmpty()) {
            throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
        }
        return questions.poll();
    }

    @Override
    public DraftSpecification generateSpecification(GenerationRequest request) {
        generationRequests.add(request);
        if (drafts.isEmpty()) {
            throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
        }
        return drafts.poll();
    }

    @Override
    public ReassessResult reassessAfterChange(ReassessRequest request) {
        reassessRequests.add(request);
        if (reassessments.isEmpty()) {
            throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
        }
        return reassessments.poll();
    }

    @Override
    public DraftSpecification editSpecification(EditRequest request) {
        editRequests.add(request);
        return next(edits);
    }

    @Override
    public DraftSpecification mergeConflict(MergeRequest request) {
        mergeRequests.add(request);
        return next(merges);
    }

    @Override
    public DraftSpecification structureImportedText(StructureImportRequest request) {
        structureRequests.add(request);
        if (structureFailure != null) {
            throw new DomainException(structureFailure);
        }
        return next(structures);
    }

    @Override
    public SourceFacts summarizeProjectSources(SummarizeSourcesRequest request) {
        summarizeRequests.add(request);
        return next(facts);
    }

    @Override
    public ProjectContextDraft buildProjectContext(BuildContextRequest request) {
        contextRequests.add(request);
        Runnable hook = onBuildContext;
        if (hook != null) {
            hook.run();
        }
        return next(contexts);
    }

    private static <T> T next(Deque<T> queue) {
        if (queue.isEmpty()) {
            throw new DomainException(ErrorCode.LLM_UNAVAILABLE);
        }
        return queue.poll();
    }

    /** Nombre total d'appels au LLM. */
    public int calls() {
        return questionRequests.size() + reassessRequests.size() + generationRequests.size() + editRequests.size()
                + mergeRequests.size() + structureRequests.size() + summarizeRequests.size() + contextRequests.size();
    }
}
