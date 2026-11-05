package dev.specwriter.support;

import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectName;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Projets en mémoire ; contient le projet par défaut, comme la base après la migration V7. */
public class InMemoryProjects implements ProjectRepository {
    private final Map<UUID, Project> projects = new LinkedHashMap<>();
    private UUID current;

    public InMemoryProjects() {
        save(Project.create(Project.DEFAULT_ID, ProjectName.of(Project.DEFAULT_NAME), null, null,
                Instant.parse("2026-11-03T00:00:00Z")));
        current = Project.DEFAULT_ID;
    }

    @Override
    public void save(Project project) {
        projects.put(project.id(), project);
    }

    @Override
    public Optional<Project> find(UUID id) {
        return Optional.ofNullable(projects.get(id));
    }

    @Override
    public Optional<Project> findByNameKey(String nameKey) {
        return projects.values().stream().filter(p -> p.nameKey().equals(nameKey)).findFirst();
    }

    @Override
    public List<Project> list() {
        return projects.values().stream().sorted(Comparator.comparing(Project::nameKey).thenComparing(Project::id)).toList();
    }

    @Override
    public void delete(UUID id) {
        projects.remove(id);
        if (id.equals(current)) {
            current = null;
        }
    }

    @Override
    public long count() {
        return projects.size();
    }

    @Override
    public Optional<UUID> currentProjectId() {
        return Optional.ofNullable(current);
    }

    @Override
    public void saveCurrentProjectId(UUID projectId) {
        current = projectId;
    }
}
