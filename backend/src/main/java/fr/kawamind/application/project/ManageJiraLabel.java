package fr.kawamind.application.project;

import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.jira.JiraLabel;
import fr.kawamind.domain.port.ClockPort;
import fr.kawamind.domain.port.JiraLabelRepository;
import fr.kawamind.domain.port.ProjectRepository;
import java.util.UUID;

/** Label de reconnaissance des tickets de spec-writer, réglé par projet (024, US1, research R1). */
public class ManageJiraLabel {

    /** {@code label} : nul sans réglage. */
    public record View(String label) {
    }

    private final ProjectRepository projects;
    private final JiraLabelRepository labels;
    private final ClockPort clock;

    public ManageJiraLabel(ProjectRepository projects, JiraLabelRepository labels, ClockPort clock) {
        this.projects = projects;
        this.labels = labels;
        this.clock = clock;
    }

    public View view(UUID projectId) {
        requireProject(projectId);
        return new View(labels.find(projectId).map(JiraLabel::value).orElse(null));
    }

    public View save(UUID projectId, String label) {
        requireProject(projectId);
        JiraLabel valid = JiraLabel.of(label);
        labels.save(projectId, valid, clock.now());
        return new View(valid.value());
    }

    public void remove(UUID projectId) {
        requireProject(projectId);
        labels.delete(projectId);
    }

    private void requireProject(UUID projectId) {
        projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
