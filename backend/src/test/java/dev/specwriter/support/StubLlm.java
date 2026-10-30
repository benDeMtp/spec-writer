package dev.specwriter.support;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.llm.EditRequest;
import dev.specwriter.domain.llm.GenerationRequest;
import dev.specwriter.domain.llm.MergeRequest;
import dev.specwriter.domain.llm.ProposeQuestionsRequest;
import dev.specwriter.domain.llm.ProposeQuestionsResult;
import dev.specwriter.domain.llm.ReassessRequest;
import dev.specwriter.domain.llm.ReassessResult;
import dev.specwriter.domain.llm.StructureImportRequest;
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
    public final List<ProposeQuestionsRequest> questionRequests = new ArrayList<>();
    public final List<GenerationRequest> generationRequests = new ArrayList<>();

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
        throw new UnsupportedOperationException();
    }

    @Override
    public DraftSpecification editSpecification(EditRequest request) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DraftSpecification mergeConflict(MergeRequest request) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DraftSpecification structureImportedText(StructureImportRequest request) {
        throw new UnsupportedOperationException();
    }
}
