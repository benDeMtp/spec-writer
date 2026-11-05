package dev.specwriter.application.agent;

import dev.specwriter.domain.agent.AgentOperation;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.session.ImplementationStatus;
import dev.specwriter.domain.session.SessionListing;
import dev.specwriter.domain.specification.SpecificationVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outil MCP {@code list_features} (FR-025) : les spécifications « prêtes à implémenter » ou « en
 * cours » d'un projet, qui ont au moins une version.
 */
public class ListFeatures {

    public record Feature(UUID id, String title, ImplementationStatus status, int latestVersion, Instant updatedAt) {
    }

    private final ProjectRepository projects;
    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final AgentJournal journal;

    public ListFeatures(ProjectRepository projects, SessionRepository sessions, SpecificationRepository versions,
            AgentJournal journal) {
        this.projects = projects;
        this.sessions = sessions;
        this.versions = versions;
        this.journal = journal;
    }

    public List<Feature> run(String agentName, UUID projectId) {
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        journal.record(agentName, AgentOperation.LIST_FEATURES, projectId, null);
        return sessions.list().stream()
                .filter(s -> s.readable() && projectId.equals(s.projectId()) && s.implementationStatus().toImplement())
                .map(this::feature).flatMap(Optional::stream).toList();
    }

    private Optional<Feature> feature(SessionListing s) {
        int latest = versions.lastNumber(s.id());
        return versions.find(s.id(), latest).map(SpecificationVersion::content)
                .map(c -> new Feature(s.id(), c.title(), s.implementationStatus(), latest, s.updatedAt()));
    }
}
