package dev.specwriter.domain.port;

import dev.specwriter.domain.project.Project;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Projets et projet courant (data-model.md de 002). Le nom est unique sans tenir compte de la casse
 * ({@code nameKey}).
 */
public interface ProjectRepository {

    void save(Project project);

    Optional<Project> find(UUID id);

    Optional<Project> findByNameKey(String nameKey);

    /** Triés par nom, sans tenir compte de la casse. */
    List<Project> list();

    void delete(UUID id);

    long count();

    /** Projet courant enregistré (FR-005) ; vide s'il n'a jamais été choisi ou s'il a été supprimé. */
    Optional<UUID> currentProjectId();

    void saveCurrentProjectId(UUID projectId);
}
