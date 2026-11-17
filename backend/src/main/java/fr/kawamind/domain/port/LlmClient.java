package fr.kawamind.domain.port;

import fr.kawamind.domain.llm.ImpactRequest;
import fr.kawamind.domain.llm.ImpactResult;
import fr.kawamind.domain.llm.CompareIssueRequest;
import fr.kawamind.domain.llm.IssueComparison;
import fr.kawamind.domain.llm.RelatedIssuesRequest;
import fr.kawamind.domain.llm.RelatedIssuesJudgement;
import fr.kawamind.domain.llm.DraftSpecification;
import fr.kawamind.domain.llm.EditRequest;
import fr.kawamind.domain.llm.GenerationRequest;
import fr.kawamind.domain.llm.MergeRequest;
import fr.kawamind.domain.llm.ProposeQuestionsRequest;
import fr.kawamind.domain.llm.ProposeQuestionsResult;
import fr.kawamind.domain.llm.ReassessRequest;
import fr.kawamind.domain.llm.ReassessResult;
import fr.kawamind.domain.llm.StructureImportRequest;

/**
 * Port du moteur de questions et de génération (contracts/llm-port.md). Les implémentations lèvent
 * une {@link fr.kawamind.domain.error.DomainException} avec {@code LLM_UNAVAILABLE},
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

    /** Tickets qui traitent du même problème que le besoin, 5 au plus (research.md R23 de 002). */
    RelatedIssuesJudgement judgeRelatedIssues(RelatedIssuesRequest request);

    /** Commun et écarts entre le besoin et un ticket non fermé (research.md R23 de 002). */
    IssueComparison compareWithIssue(CompareIssueRequest request);

    /** Modules impactés et échanges supposés, parmi les éléments candidats (research.md R22 de 002). */
    ImpactResult identifyImpact(ImpactRequest request);
}
