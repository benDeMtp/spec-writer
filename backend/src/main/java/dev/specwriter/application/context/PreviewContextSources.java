package dev.specwriter.application.context;

import dev.specwriter.application.jira.JiraAccess;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.DocSourceRepository;
import dev.specwriter.domain.port.DocumentationReader;
import dev.specwriter.domain.port.DocumentationReader.Unread;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.domain.port.IssueTracker;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ce qui sera envoyé à Copilot avant la génération de la fiche (FR-013b) : le nombre de pages
 * annoncées par chaque source et de tickets, sans lire le contenu d'aucune page. Une source qui
 * n'annonce aucune page est signalée avec le motif donné par le lecteur.
 */
public class PreviewContextSources {

    /** Pages et tickets qui seraient lus, et sources inutilisables. */
    public record Preview(int pages, int tickets, List<Unread> unreadable) {
    }

    private final ProjectRepository projects;
    private final DocSourceRepository docSources;
    private final DocumentationReader reader;
    private final JiraAccess jira;
    private final IssueTracker tracker;

    public PreviewContextSources(ProjectRepository projects, DocSourceRepository docSources, DocumentationReader reader,
            JiraAccess jira, IssueTracker tracker) {
        this.projects = projects;
        this.docSources = docSources;
        this.reader = reader;
        this.jira = jira;
        this.tracker = tracker;
    }

    public Preview run(UUID projectId) {
        Project project = projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
        int pages = 0;
        List<Unread> unreadable = new ArrayList<>();
        for (DocSource source : docSources.list(projectId)) {
            DocumentationReader.Availability count = reader.count(source);
            if (count.pages() == 0) {
                unreadable.add(new Unread(source.location(),
                        count.problem() == null ? UnreadReason.UNREACHABLE : count.problem()));
            }
            pages += count.pages();
        }
        return new Preview(pages, ProjectTickets.count(jira, tracker, project), List.copyOf(unreadable));
    }
}
