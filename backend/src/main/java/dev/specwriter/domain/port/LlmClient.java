package dev.specwriter.domain.port;

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

/**
 * Port du moteur de questions et de génération (contracts/llm-port.md). Les implémentations lèvent
 * une {@link dev.specwriter.domain.error.DomainException} avec {@code LLM_UNAVAILABLE},
 * {@code LLM_INVALID_OUTPUT}, {@code MODEL_UNAVAILABLE}, {@code QUOTA_EXHAUSTED} ou
 * {@code COPILOT_NOT_CONNECTED}.
 */
public interface LlmClient {

    ProposeQuestionsResult proposeQuestions(ProposeQuestionsRequest request);

    ReassessResult reassessAfterChange(ReassessRequest request);

    DraftSpecification generateSpecification(GenerationRequest request);

    DraftSpecification editSpecification(EditRequest request);

    DraftSpecification mergeConflict(MergeRequest request);

    DraftSpecification structureImportedText(StructureImportRequest request);

    /** Faits sourcés d'un lot de pages et de tickets (research.md R4 de 002). */
    SourceFacts summarizeProjectSources(SummarizeSourcesRequest request);

    /** Fiche de contexte rédigée à partir des faits et de la fiche actuelle (research.md R4 de 002). */
    ProjectContextDraft buildProjectContext(BuildContextRequest request);
}
