package dev.specwriter.application.context;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import java.util.UUID;

/**
 * Accepte une fiche proposée par l'IA (FR-013a) ; son origine devient GENERATED. Refusée si la fiche
 * a changé depuis la proposition ({@code CONTEXT_CHANGED}) : il faut alors régénérer ou garder la
 * fiche actuelle.
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

    public Project run(UUID projectId, UUID proposalId) {
        Project project = projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        ContextProposalStore.ContextProposal proposal = proposals.take(projectId, proposalId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Cette proposition n'existe plus. Régénérez-la."));
        if (!proposal.baseContextHash().equals(project.context().hash())) {
            throw new DomainException(ErrorCode.CONTEXT_CHANGED);
        }
        Project accepted = project.withContext(ProjectContext.generated(proposal.markdown(), clock.now()));
        projects.save(accepted);
        return accepted;
    }
}
