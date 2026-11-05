package dev.specwriter.domain.project;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Projet qui regroupe des spécifications (data-model.md de 002, FR-001 à FR-006). Immuable :
 * chaque changement crée une copie. La clé Jira, facultative, surcharge le projet Jira par défaut
 * des réglages. Sa fiche générale, rédigée à la main, guide toutes les opérations de l'IA et
 * l'emporte sur les autres sources de la connaissance (FR-008).
 */
public record Project(
        UUID id,
        ProjectName name,
        String description,
        JiraProjectKey jiraProjectKey,
        ProjectContext context,
        Instant createdAt,
        Instant updatedAt) {

    /** Créé par la migration V7, qui y range les sessions existantes (SC-001). */
    public static final UUID DEFAULT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final String DEFAULT_NAME = "Projet par défaut";
    public static final int MAX_DESCRIPTION = 1_000;

    public Project {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        description = validDescription(description);
        context = context == null ? ProjectContext.NONE : context;
    }

    public static Project create(UUID id, ProjectName name, String description, JiraProjectKey jiraProjectKey,
            Instant now) {
        return new Project(id, name, description, jiraProjectKey, ProjectContext.NONE, now, now);
    }

    public Project update(ProjectName newName, String newDescription, JiraProjectKey newKey, Instant now) {
        return new Project(id, newName, newDescription, newKey, context, createdAt, now);
    }

    /** Nouvelle version de la fiche générale, modifiée à la main (FR-008). */
    public Project withContext(ProjectContext newContext) {
        return new Project(id, name, description, jiraProjectKey, newContext, createdAt, newContext.updatedAt());
    }

    public String nameKey() {
        return name.nameKey();
    }

    /** Projet Jira des actions du projet : sa clé, sinon le projet par défaut des réglages (FR-006). */
    public Optional<String> effectiveJiraProject(String defaultProjectKey) {
        return jiraProjectKey != null ? Optional.of(jiraProjectKey.value()) : Optional.ofNullable(defaultProjectKey);
    }

    private static String validDescription(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.strip();
        if (value.length() > MAX_DESCRIPTION) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "La description dépasse 1 000 caractères.");
        }
        return value;
    }
}
