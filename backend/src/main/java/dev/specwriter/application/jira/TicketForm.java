package dev.specwriter.application.jira;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.IssueType;
import dev.specwriter.domain.jira.RequiredField;
import dev.specwriter.domain.port.IssueTracker;
import java.util.List;
import java.util.Set;

/** Règles communes à l'aperçu et à la création d'un ticket (FR-036a, FR-036b). */
final class TicketForm {

    /** Champs remplis par spec-writer ou par Jira lui-même : jamais demandés à l'utilisateur. */
    static final Set<String> FILLED = Set.of("summary", "description", "project", "issuetype", "reporter");

    private TicketForm() {
    }

    static List<IssueType> types(IssueTracker tracker, JiraAccess.Target target) {
        try {
            return tracker.issueTypes(target.endpoint(), target.requireProject());
        } catch (IssueTrackerException e) {
            throw e.toDomain();
        }
    }

    /** « Story » par défaut s'il existe, sinon le premier type du projet. */
    static IssueType defaultType(List<IssueType> types) {
        if (types.isEmpty()) {
            throw new DomainException(ErrorCode.JIRA_REJECTED, "Le projet Jira ne propose aucun type de ticket créable.");
        }
        return types.stream().filter(t -> IssueType.STORY.equalsIgnoreCase(t.name())).findFirst().orElse(types.getFirst());
    }

    static IssueType chosen(List<IssueType> types, String issueTypeId) {
        if (issueTypeId == null || issueTypeId.isBlank()) {
            return defaultType(types);
        }
        return types.stream().filter(t -> t.id().equals(issueTypeId)).findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_FAILED, "Ce type de ticket n'existe pas dans le projet."));
    }

    static List<RequiredField> toFill(IssueTracker tracker, JiraAccess.Target target, String issueTypeId) {
        try {
            return tracker.requiredFields(target.endpoint(), target.requireProject(), issueTypeId).stream()
                    .filter(f -> !FILLED.contains(f.id()))
                    .toList();
        } catch (IssueTrackerException e) {
            throw e.toDomain();
        }
    }
}
