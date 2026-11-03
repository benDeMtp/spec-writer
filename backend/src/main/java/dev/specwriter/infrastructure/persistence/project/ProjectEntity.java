package dev.specwriter.infrastructure.persistence.project;

import dev.specwriter.infrastructure.persistence.InstantTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "project")
public class ProjectEntity {
    @Id
    public String id;

    @Column(nullable = false)
    public String name;

    @Column(name = "name_key", nullable = false, unique = true)
    public String nameKey;

    public String description;

    @Column(name = "jira_project_key")
    public String jiraProjectKey;

    @Convert(converter = InstantTextConverter.class)
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Convert(converter = InstantTextConverter.class)
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
