package fr.kawamind.application.agent;

import fr.kawamind.domain.agent.AgentOperation;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.port.SpecificationRepository;
import fr.kawamind.domain.session.ImplementationStatus;
import fr.kawamind.domain.session.SessionListing;
import fr.kawamind.domain.specification.SpecificationVersion;
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
