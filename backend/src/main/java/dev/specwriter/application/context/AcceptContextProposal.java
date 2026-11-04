package dev.specwriter.application.context;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.ContextSheet;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import java.util.Set;
import java.util.UUID;

/**
 * Accepte les fiches choisies d'une proposition de l'IA (FR-008a, FR-013a) ; leur origine devient
 * GENERATED. Une fiche qui a changé depuis la proposition est refusée ({@code CONTEXT_CHANGED}) : il
 * faut alors régénérer ou garder la fiche actuelle. L'autre fiche reste acceptable.
 */
public class AcceptContextProposal {

    private final ProjectRepository projects;
    private final ContextProposalStore proposals;
    private final ClockPort clock;

    public AcceptContextProposal(ProjectRepository projects, ContextProposalStore proposals, ClockPort clock) {
        this.projects = projects;
        this.proposals = proposals;
        this.clock = clock;
    }

    public Project run(UUID projectId, UUID proposalId, Set<ContextSheet> sheets) {
        if (sheets == null || sheets.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Choisissez la fiche à accepter.");
        }
        Project project = projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        ContextProposalStore.ContextProposal proposal = proposals.find(projectId, proposalId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Cette proposition n'existe plus. Régénérez-la."));
        for (ContextSheet sheet : sheets) {
            ContextProposalStore.ProposedSheet proposed = proposal.sheet(sheet);
            if (proposed.accepted()) {
                throw new DomainException(ErrorCode.NOT_FOUND, "Cette fiche a déjà été acceptée.");
            }
            if (!proposed.baseHash().equals(project.context(sheet).hash())) {
                throw new DomainException(ErrorCode.CONTEXT_CHANGED);
            }
        }
        Project accepted = project;
        for (ContextSheet sheet : sheets) {
            accepted = accepted.withContext(sheet, ProjectContext.generated(proposal.sheet(sheet).markdown(), clock.now()));
        }
        projects.save(accepted);
        proposals.markAccepted(proposal, sheets);
        return accepted;
    }
}
