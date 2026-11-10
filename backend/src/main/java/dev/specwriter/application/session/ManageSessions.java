package dev.specwriter.application.session;

import dev.specwriter.application.project.CurrentProject;
import dev.specwriter.application.specification.OperationRegistry;
import dev.specwriter.application.specification.ProposalStore;
import dev.specwriter.domain.conversation.SpecificationDiscussion;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.ClockPort;
import dev.specwriter.domain.port.ConversationRepository;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.port.SpecificationDiscussionRepository;
import dev.specwriter.domain.port.SpecificationRepository;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.session.SessionListing;
import dev.specwriter.domain.specification.SpecificationVersion;
import java.util.List;
import java.util.UUID;

/**
 * Lister les sessions du projet courant (FR-005 de 002), renommer et supprimer des sessions, lister
 * leurs versions (FR-017, FR-018).
 */
public class ManageSessions {

    public record Entry(SessionListing session, int latestVersion) {
    }

    private final SessionRepository sessions;
    private final SpecificationRepository versions;
    private final ProjectRepository projects;
    private final ClockPort clock;
    private final ProposalStore proposals;
    private final OperationRegistry operations;
    private final ConversationRepository conversations;
    private final SpecificationDiscussionRepository discussions;

    public ManageSessions(SessionRepository sessions, SpecificationRepository versions, ProjectRepository projects,
            ClockPort clock) {
        this(sessions, versions, projects, clock, null, new OperationRegistry());
    }

    /** {@code proposals} : propositions de l'IA purgées avec la session ; {@code operations} : refus pendant l'IA. */
    public ManageSessions(SessionRepository sessions, SpecificationRepository versions, ProjectRepository projects,
            ClockPort clock, ProposalStore proposals, OperationRegistry operations) {
        this(sessions, versions, projects, clock, proposals, operations, null, null);
    }

    /** {@code discussions} : discussions de la spécification, renommées et supprimées avec elle (011, US3). */
    public ManageSessions(SessionRepository sessions, SpecificationRepository versions, ProjectRepository projects,
            ClockPort clock, ProposalStore proposals, OperationRegistry operations, ConversationRepository conversations,
            SpecificationDiscussionRepository discussions) {
        this.conversations = conversations;
        this.discussions = discussions;
        this.sessions = sessions;
        this.versions = versions;
        this.projects = projects;
        this.clock = clock;
        this.proposals = proposals;
        this.operations = operations;
    }

    /** Sessions du projet courant, les plus récemment modifiées d'abord. */
    public List<Entry> list() {
        UUID current = CurrentProject.resolve(projects).id();
        return sessions.list().stream()
                .filter(s -> current.equals(s.projectId()))
                .map(s -> new Entry(s, s.readable() ? versions.lastNumber(s.id()) : 0))
                .toList();
    }

    public Session rename(UUID id, String name) {
        Session session = existing(id).rename(name, clock.now());
        sessions.save(session, sessions.questionnaire(id).orElseThrow());
        if (discussions != null) {
            discussions.renameAll(id, session.name());
        }
        return session;
    }

    /** Nombre de discussions rattachées, pour la confirmation de suppression (011, research.md R7). */
    public int discussions(UUID id) {
        existing(id);
        return discussions == null ? 0 : discussions.bySession(id).size();
    }

    /**
     * Suppression physique de la session, de ses questions, réponses, versions et lien Jira, refusée
     * pendant une opération de l'IA (FR-016, FR-019 de 002). Jira n'est pas modifié.
     */
    public void delete(UUID id) {
        delete(id, false);
    }

    /**
     * {@code keepDiscussions} faux : les discussions rattachées sont supprimées avec la spécification ;
     * vrai : elles restent, marquées « spécification supprimée » (011, FR-016). Refusée pendant une
     * réponse ou une proposition dans l'une d'elles.
     */
    public void delete(UUID id, boolean keepDiscussions) {
        if (sessions.list().stream().noneMatch(s -> s.id().equals(id))) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        List<UUID> attached = discussions == null ? List.of()
                : discussions.bySession(id).stream().map(SpecificationDiscussion::conversationId).toList();
        if (operations.running(id) || attached.stream().anyMatch(operations::running)) {
            throw new DomainException(ErrorCode.OPERATION_IN_PROGRESS,
                    "L'IA travaille sur cette spécification. Attendez la fin de l'opération, puis supprimez-la.");
        }
        if (keepDiscussions && discussions != null) {
            discussions.detachAll(id);
        } else if (conversations != null) {
            attached.forEach(conversations::delete);
        }
        versions.deleteAll(id);
        sessions.delete(id);
        if (proposals != null) {
            proposals.discard(id);
        }
    }

    public List<SpecificationVersion> versions(UUID id) {
        existing(id);
        return versions.list(id);
    }

    private Session existing(UUID id) {
        return sessions.find(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }
}
