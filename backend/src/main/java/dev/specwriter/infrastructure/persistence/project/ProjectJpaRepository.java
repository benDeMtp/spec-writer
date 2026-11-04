package dev.specwriter.infrastructure.persistence.project;

import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.JiraProjectKey;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import dev.specwriter.domain.project.ProjectName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ProjectJpaRepository implements ProjectRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void save(Project p) {
        ProjectEntity e = new ProjectEntity();
        e.id = p.id().toString();
        e.name = p.name().value();
        e.nameKey = p.nameKey();
        e.description = p.description();
        e.jiraProjectKey = p.jiraProjectKey() == null ? null : p.jiraProjectKey().value();
        e.context = p.context().markdown();
        e.contextHash = p.context().hash();
        e.contextUpdatedAt = p.context().updatedAt();
        e.createdAt = p.createdAt();
        e.updatedAt = p.updatedAt();
        em.merge(e);
    }

    @Override
    @Transactional
    public Optional<Project> find(UUID id) {
        return Optional.ofNullable(em.find(ProjectEntity.class, id.toString())).map(ProjectJpaRepository::toDomain);
    }

    @Override
    @Transactional
    public Optional<Project> findByNameKey(String nameKey) {
        return em.createQuery("from ProjectEntity where nameKey = :k", ProjectEntity.class).setParameter("k", nameKey)
                .getResultStream().findFirst().map(ProjectJpaRepository::toDomain);
    }

    @Override
    @Transactional
    public List<Project> list() {
        return em.createQuery("from ProjectEntity order by nameKey, id", ProjectEntity.class).getResultStream()
                .map(ProjectJpaRepository::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        ProjectEntity e = em.find(ProjectEntity.class, id.toString());
        if (e != null) {
            em.remove(e);
        }
    }

    @Override
    @Transactional
    public long count() {
        return em.createQuery("select count(p) from ProjectEntity p", Long.class).getSingleResult();
    }

    @Override
    @Transactional
    public Optional<UUID> currentProjectId() {
        AppSettingsEntity s = em.find(AppSettingsEntity.class, 1);
        return s == null || s.currentProjectId == null ? Optional.empty() : Optional.of(UUID.fromString(s.currentProjectId));
    }

    @Override
    @Transactional
    public void saveCurrentProjectId(UUID projectId) {
        AppSettingsEntity s = Optional.ofNullable(em.find(AppSettingsEntity.class, 1)).orElseGet(AppSettingsEntity::new);
        s.currentProjectId = projectId == null ? null : projectId.toString();
        em.merge(s);
    }

    private static Project toDomain(ProjectEntity e) {
        return new Project(UUID.fromString(e.id), new ProjectName(e.name), e.description,
                JiraProjectKey.ofNullable(e.jiraProjectKey), new ProjectContext(e.context, e.contextHash,
                        e.contextUpdatedAt), e.createdAt, e.updatedAt);
    }
}
